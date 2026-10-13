//! A small, single-photo Android façade over the upstream engine. No desktop UI is linked.
#![cfg_attr(not(target_os = "android"), forbid(unsafe_code))]
#![cfg_attr(target_os = "android", deny(unsafe_code))]
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use lightcraft_develop::{DevelopSettings, Preset};
use lightcraft_engine::{
    Session,
    catalog::{Op, PhotoId},
};
use serde::Deserialize;
use serde_json::{Value, json};
use std::{collections::BTreeMap, path::PathBuf, sync::Arc};

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "camelCase", deny_unknown_fields)]
pub struct Edit {
    pub preset: Option<String>,
    pub amount: f64,
    pub controls: BTreeMap<String, f64>,
    pub crop: Option<[f64; 4]>,
    pub rotation: u8,
}

impl Default for Edit {
    fn default() -> Self {
        Self { preset: None, amount: 100.0, controls: BTreeMap::new(), crop: None, rotation: 0 }
    }
}

pub struct Mobile {
    session: Session,
    photo: Option<PhotoId>,
    base: DevelopSettings,
    presets_path: PathBuf,
}

impl Mobile {
    pub fn new(folder: PathBuf) -> Result<Self, String> {
        std::fs::create_dir_all(&folder).map_err(|e| e.to_string())?;
        let path = folder.join("presets.lcpreset");
        let mut session = Session::new().with_fs();
        if path.exists() {
            session.execute("preset.import", &json!({"paths": [path]})).map_err(|e| e.to_string())?;
        }
        Ok(Self { session, photo: None, base: DevelopSettings::default(), presets_path: path })
    }

    pub fn presets(&self) -> Value {
        json!(self.session.presets.iter().map(|p| json!({"id": p.id, "name": p.name, "group": p.group})).collect::<Vec<_>>())
    }

    fn settings(&self, edit: &Edit) -> Result<DevelopSettings, String> {
        if !edit.amount.is_finite() || !(0.0..=200.0).contains(&edit.amount) {
            return Err("Intensidade inválida".into());
        }
        let mut settings = self.base.clone();
        if let Some(id) = &edit.preset {
            let p = self.session.presets.iter().find(|p| &p.id == id).ok_or("Preset não encontrado")?;
            settings = p.apply(&settings, edit.amount / 100.0);
        }
        for (id, value) in &edit.controls {
            if !value.is_finite() || !lightcraft_develop::controls::set(&mut settings, id, *value) {
                return Err(format!("Ajuste inválido: {id}"));
            }
        }
        settings.orientation = lightcraft_geom::Orientation::from_exif(match edit.rotation % 4 {
            1 => 6,
            2 => 3,
            3 => 8,
            _ => 1,
        });
        if let Some([x0, y0, x1, y1]) = edit.crop {
            if ![x0, y0, x1, y1].iter().all(|v| v.is_finite() && (0.0..=1.0).contains(v)) || x1 - x0 < 0.05 || y1 - y0 < 0.05 {
                return Err("Recorte inválido".into());
            }
            settings.crop.geometry.rect = lightcraft_geom::Rect::new(x0, y0, x1, y1);
        }
        Ok(settings)
    }

    fn render(&mut self, edit: &Edit, edge: usize, path: &str) -> Result<Value, String> {
        let id = self.photo.ok_or("Importe uma foto primeiro")?;
        let settings = self.settings(edit)?;
        let controls: BTreeMap<_, _> = lightcraft_develop::CONTROLS
            .iter()
            .filter_map(|spec| lightcraft_develop::controls::get(&settings, spec.id).map(|v| (spec.id, v)))
            .collect();
        self.session
            .catalog
            .apply(Op::SetDevelop { id, settings: Arc::new(settings), label: "Mobile edit".into(), edited: None })
            .map_err(|e| e.to_string())?;
        let rendered = self.session.render_now(id, edge.clamp(64, 4096), edge.clamp(64, 4096))?;
        let options = lightcraft_engine::export::ExportOptions { quality: 94, ..Default::default() };
        let bytes = lightcraft_engine::export::encode_image(&rendered.image, &options)?;
        std::fs::write(path, bytes).map_err(|e| e.to_string())?;
        Ok(json!({"path": path, "width": rendered.image.width, "height": rendered.image.height, "controls": controls}))
    }

    pub fn dispatch(&mut self, request: &Value) -> Result<Value, String> {
        let text = |key: &str| request.get(key).and_then(Value::as_str).ok_or_else(|| format!("Campo ausente: {key}"));
        match text("method")? {
            "presets" => Ok(self.presets()),
            "open" => {
                let path = text("path")?;
                // A new photo releases the previous photo's decoded sources and undo history.
                let presets: Vec<Preset> = self.session.presets.iter().cloned().collect();
                let mut next = Session::new().with_fs();
                next.presets.clear();
                next.presets.extend(presets);
                let report = next.execute("library.import", &json!({"paths": [path]})).map_err(|e| e.to_string())?;
                let id = report
                    .get("imported")
                    .and_then(Value::as_array)
                    .and_then(|a| a.first())
                    .and_then(Value::as_u64)
                    .map(PhotoId)
                    .ok_or_else(|| format!("Não foi possível abrir a foto: {report}"))?;
                let photo = next.catalog.photo(id).ok_or("Foto não encontrada")?;
                let dimensions = json!({"width": photo.width, "height": photo.height, "warnings": report.get("warnings")});
                // Importing a DNG photo may pick up its embedded edit. Keep it as the base.
                let base = (*photo.develop).clone();
                // Validate the pixel decode before replacing the current editor. A file
                // with a readable header but corrupt pixels must leave the old photo usable.
                next.render_now(id, 1440, 1440)?;
                self.base = base;
                self.photo = Some(id);
                self.session = next;
                Ok(dimensions)
            }
            "importPresets" => {
                let report = self.session.execute("preset.import", &json!({"paths": [text("path")?]})).map_err(|e| e.to_string())?;
                // Persist with an atomic replacement; never overwrite the imported preset file.
                let user: Vec<_> = self.session.presets.iter().filter(|p| !p.builtin).cloned().collect();
                let tmp = self.presets_path.with_extension("tmp");
                std::fs::write(&tmp, lightcraft_engine::presets::to_lcpreset(&user)).map_err(|e| e.to_string())?;
                std::fs::rename(&tmp, &self.presets_path).map_err(|e| e.to_string())?;
                Ok(json!({"report": report, "presets": self.presets()}))
            }
            "render" => {
                let edit: Edit = serde_json::from_value(request.get("edit").cloned().unwrap_or(json!({}))).map_err(|e| e.to_string())?;
                let edge = request.get("edge").and_then(Value::as_u64).unwrap_or(1440).min(4096) as usize;
                self.render(&edit, edge, text("path")?)
            }
            _ => Err("Operação desconhecida".into()),
        }
    }
}

#[cfg(target_os = "android")]
mod android {
    use super::*;
    use jni::{
        JNIEnv,
        objects::{JObject, JString},
        sys::jstring,
    };
    use std::sync::{Mutex, OnceLock};
    static MOBILE: OnceLock<Mutex<Option<Mobile>>> = OnceLock::new();

    // SAFETY: fixed JNI symbol, called only by NativeEngine with valid JVM references.
    // There is no raw-pointer manipulation. All conversions use jni's checked safe API.
    #[allow(unsafe_code)] // The exported symbol attribute only; never unsafe blocks.
    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_lightcraft_mobile_NativeEngine_execute(mut env: JNIEnv, _this: JObject, input: JString) -> jstring {
        let result = lightcraft_engine::guard::catch("Android editor", || -> Result<Value, String> {
            let raw: String = env.get_string(&input).map_err(|e| e.to_string())?.into();
            let request: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;
            let mut mobile = MOBILE.get_or_init(|| Mutex::new(None)).lock().map_err(|_| "Editor indisponível; reabra o aplicativo")?;
            if request.get("method").and_then(Value::as_str) == Some("init") {
                let folder = request.get("folder").and_then(Value::as_str).ok_or("Pasta ausente")?;
                *mobile = Some(Mobile::new(folder.into())?);
                return mobile.as_ref().map(|m| m.presets()).ok_or("Editor indisponível".into());
            }
            mobile.as_mut().ok_or("Editor não inicializado")?.dispatch(&request)
        })
        .and_then(|r| r);
        let reply = match result {
            Ok(value) => json!({"ok": true, "data": value}),
            Err(e) => json!({"ok": false, "error": e}),
        };
        match env.new_string(reply.to_string()) {
            Ok(s) => s.into_raw(),
            Err(_) => std::ptr::null_mut(), // JVM already carries the allocation exception.
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn editor() -> Mobile {
        Mobile { session: Session::new(), photo: None, base: DevelopSettings::default(), presets_path: PathBuf::new() }
    }
    #[test]
    fn preset_amount_is_idempotent_and_manual_edits_win() {
        let mut m = editor();
        m.session.presets.push(Preset {
            id: "test".into(),
            name: "Test".into(),
            group: "Test".into(),
            settings: json!({"light":{"exposure":2.0}}),
            favorite: false,
            builtin: false,
        });
        let mut edit = Edit { preset: Some("test".into()), amount: 50.0, ..Default::default() };
        assert_eq!(m.settings(&edit).unwrap().light.exposure, 1.0);
        assert_eq!(m.settings(&edit).unwrap(), m.settings(&edit).unwrap());
        edit.controls.insert("light.exposure".into(), 0.3);
        assert_eq!(m.settings(&edit).unwrap().light.exposure, 0.3);
    }
    #[test]
    fn invalid_crop_and_nonfinite_controls_are_rejected() {
        let m = editor();
        assert!(m.settings(&Edit { crop: Some([0.8, 0.0, 0.2, 1.0]), ..Default::default() }).is_err());
        assert!(m.settings(&Edit { amount: f64::NAN, ..Default::default() }).is_err());
    }
}
