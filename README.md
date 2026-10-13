# Lightcraft Mobile

Editor Android derivado de [storytold/lightcraft](https://github.com/storytold/lightcraft).
Interface Kotlin / Jetpack Compose e processamento local com o motor Rust do LightCraft.

## Recursos

- Importar fotos da galeria ou de arquivos, incluindo DNG.
- Importar presets DNG, XMP, `.lrtemplate`, `.lcpreset` e pacotes ZIP.
- Faixa horizontal com prévias da própria foto, grupos, favoritos (pressione um preset) e intensidade 0–200%.
- Exposição, contraste, realces, sombras, brancos, pretos, saturação, vibração, temperatura, matiz,
  clareza, textura, desembaçar, vinheta e granulação.
- Recorte livre com cantos arrastáveis, proporções iniciais 1:1 / 4:5 / 3:2 / 16:9 e rotação de 90°.
- Segurar a foto para comparar com o original; desfazer até 40 alterações e redefinir a edição.
- Recuperar foto e edição ao reabrir o app; presets e favoritos persistem no aparelho.
- Exportar JPEG sRGB, qualidade 94, até 4096 px no lado maior, em `Pictures/Lightcraft`.

O original é preservado. O aplicativo não pede acesso amplo ao armazenamento, contas ou internet.

**Estágio: alpha.** A aparência dos presets Lightroom é aproximada, porque o motor é diferente.
Perfis e looks da Adobe não são reproduzidos; ajustes não transferidos são informados na importação.
Presets DNG precisam conter ajustes XMP. Corte, geometria e balanço de branco personalizado da foto
doadora são excluídos ao transformar o DNG em preset.

## Compilar

JDK 21, SDK 36, Build Tools 36, NDK `28.2.13676358`, Rust estável ≥ 1.95 e ferramentas nativas
de compilação da plataforma. Configure `ANDROID_HOME` e `JAVA_HOME`; `cargo` e `rustup` no PATH.

```powershell
# Windows: Build Tools Visual Studio com C++ ou Rust gnullvm + LLVM-MinGW.
.\scripts\build-android.ps1 -Abi all
# Alternativa gnullvm:
.\scripts\build-android.ps1 -Abi all -RustToolchain stable-x86_64-pc-windows-gnullvm
```

```sh
# Linux / macOS
bash scripts/build-android.sh
```

APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`.
Android 10+; `arm64-v8a` para aparelhos e `x86_64` para emuladores.
Abra `apps/android` no Android Studio; compile primeiro as bibliotecas nativas.
O build falha se o motor nativo estiver ausente.

## Verificar

```sh
cargo fmt -p lightcraft-mobile --check
cargo clippy -p lightcraft-mobile -- -D warnings
cargo test -p lightcraft-mobile
cd apps/android
./gradlew lintDebug connectedDebugAndroidTest
```

Os testes exercitam JNI real, importação de DNG-preset sintético, aplicação, intensidade zero, recorte,
rotação, persistência, erro de leitura, interface e exportação. As imagens são geradas nos testes.
O [workflow Android](.github/workflows/android.yml) gera o APK de desenvolvimento como artefato.

Arquitetura e limites: [docs/android.md](docs/android.md).
Documentação original: [README.upstream.md](README.upstream.md).

MIT ou Apache-2.0, como o original: veja `LICENSE-MIT`, `LICENSE-APACHE` e `NOTICE`.
O ícone e a interface mobile são originais; não usam recursos visuais do VSCO ou da Adobe.
