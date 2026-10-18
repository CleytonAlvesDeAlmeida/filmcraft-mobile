# FilmCraft Mobile

Editor de vídeo simples para **Android**, feito com a abordagem "UI nativa + núcleo Rust":

- **UI:** Kotlin + Jetpack Compose (pensada para toque).
- **Núcleo:** Rust (`rust/mobile-core`), que usa o `filmcraft-time` do projeto
  [storytold/filmcraft](https://github.com/storytold/filmcraft) (tempo exato em inteiros, quadros e timecode).
- **Ponte:** JNI com JSON em texto. O Rust não guarda estado; a UI guarda o projeto como texto.
- **Preview e exportação:** Media3 (ExoPlayer + Transformer), com codecs de hardware do aparelho.

Licença: MIT OR Apache-2.0 (a mesma do FilmCraft).

## O que o app faz hoje (v0.1)

- Importar vários vídeos (seletor de fotos do Android)
- Linha do tempo com uma faixa de clipes, com miniaturas
- Cortar início e fim de cada clipe (deslizador, alinhado a quadros)
- Dividir no cursor, duplicar, excluir, reordenar, silenciar (na exportação)
- Preview ao vivo da linha do tempo
- Exportar MP4 (H.264/AAC) para `Filmes/FilmCraft`

Limitações conhecidas: sem salvar projeto (ainda), sem textos/filtros/transições, o primeiro clipe define
o formato da saída, "Som: off" afeta só a exportação, os vídeos vêm do seletor de fotos (acesso vale
enquanto o app está em uso).

## Como compilar no Codemagic

1. Suba esta pasta como um repositório novo no GitHub/GitLab/Bitbucket.
2. No Codemagic: **Add application**, escolha o repositório e o tipo **codemagic.yaml**.
3. Abra `codemagic.yaml` e troque `seu-email@exemplo.com` pelo seu e-mail.
4. Rode o workflow **android-debug**. O APK aparece em *Artifacts* ao fim da build.

O workflow instala Rust e `cargo-ndk`, instala o NDK, roda os testes do núcleo, compila as `.so`
(arm64-v8a, armeabi-v7a, x86_64) e depois chama o Gradle. Primeira build: ~10 a 15 min.

Para publicar na Google Play, use **android-release**: envie seu keystore em
*Teams > Code signing identities > Android keystores* com o nome `filmcraft_keystore`.
O `applicationId` atual é `app.filmcraft.mobile`; troque em `app/build.gradle.kts` (e em `namespace`)
se quiser outro. Se mudar o pacote Kotlin do `NativeCore`, renomeie também as funções
`Java_app_filmcraft_mobile_core_NativeCore_*` em `rust/mobile-core/src/jni_bridge.rs`.

## Compilar localmente

```bash
# 1. Rust (>= 1.95) + alvos Android + cargo-ndk + NDK (ANDROID_NDK_HOME)
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked

# 2. Testes do núcleo (no computador)
cd rust && cargo test && cd ..

# 3. Bibliotecas Android + Gradle
bash scripts/build-rust.sh release
bash scripts/ensure-gradle-wrapper.sh
./gradlew :app:assembleDebug
```

## Estrutura

```
rust/mobile-core/src/timeline.rs    modelo da linha do tempo (adicionar, cortar, dividir, mover...) + testes
rust/mobile-core/src/lib.rs         API em JSON (apply_json, new_project_json)
rust/mobile-core/src/jni_bridge.rs  funções JNI chamadas pelo Kotlin
app/.../core/NativeCore.kt          ponte Kotlin <-> Rust
app/.../EditorViewModel.kt          estado da tela
app/.../ui/EditorScreen.kt          interface (Compose)
app/.../export/Exporter.kt          exportação com Media3 Transformer
codemagic.yaml                      CI
```

## Para evoluir

1. **Salvar/abrir projeto:** o estado já é JSON. Falta gravar em arquivo e persistir permissão das URIs.
2. **Mais edição no Rust:** hoje o modelo é próprio e pequeno. O passo natural é usar `filmcraft-edit`
   e `filmcraft-project` (trim, ripple, roll, slip, marcadores) no lugar dele.
3. **Texto, filtros e transições:** efeitos do Media3 (`Effects`) na exportação e no preview.
4. **Trilhas de áudio e legendas:** o FilmCraft já tem as crates `captions`, `audio-dsp` e `speech`.
5. **Render próprio:** o compositor `render`/`gpu` do FilmCraft (wgpu) poderia substituir o Transformer.
