# StoryCut (protótipo Android)

Projeto-fonte para Android Studio. Abra a pasta `StoryCut30`, sincronize o Gradle e use **Build > Build APK(s)**. Necessita Android SDK 35, JDK 17 e internet na primeira sincronização para baixar Media3.

## Funcionalidades
- Seleção de vídeo por seletor nativo de arquivos, sem limite arbitrário de tamanho.
- Duração configurável de 1 a 3600 segundos, padrão 30.
- Exportação sequencial em MP4 usando Media3 Transformer; último trecho menor.
- Nomes ordenáveis `take_0001.mp4`, `take_0002.mp4` etc.
- Compartilhamento assistido, um take por vez, via intent do Instagram Stories.

## Limitações importantes
- **Não publica automaticamente** em conta pessoal nem confirma publicação; o usuário precisa publicar cada Story e retornar ao app. O botão avança ao abrir o editor, não após verificar que o Story foi publicado.
- A opção empresarial é visual/preparatória; integração com API Meta não implementada.
- Processamento local depende de espaço livre, codecs suportados e desempenho do aparelho. A exportação pode recodificar e não garante qualidade bit a bit idêntica nem precisão ao frame para todo formato de entrada.
- Para produção: acrescentar serviço em primeiro plano para exports longos, persistência de fila após encerramento, barra de progresso, cancelamento, testes em dispositivos e tratamento de falhas de codecs.
- Projeto inicial não testado em compilação neste ambiente (SDK Android/Gradle não instalados).

## Compilação pelo GitHub Actions (sem computador)
O workflow `.github/workflows/android.yml` usa Gradle 8.9 e JDK 17 para gerar `app-debug.apk` como artefato. Envie o conteúdo desta pasta (não a pasta externa) à raiz de um repositório GitHub, abra Actions > Gerar APK StoryCut > Run workflow, e baixe o artefato da execução concluída. O código ainda não foi compilado/testado aqui; falhas de build precisam ser corrigidas conforme logs.
