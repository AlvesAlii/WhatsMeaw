# VaultView (Android)

Arquiva mídias de visualização única do WhatsApp copiando o arquivo no instante
em que ele é escrito na pasta de mídia, antes do app oficial apagá-lo.
Sem root. Sem servidor. Uso pessoal.

## Gerar o APK sem Android Studio
1. Suba este projeto em um repositório privado no GitHub.
2. Aba Actions > build-apk > Run workflow.
3. Baixe o artefato `vaultview-apk` e instale o `app-debug.apk`
   (é preciso permitir "instalar de fontes desconhecidas").

Com Android Studio: abra a pasta e use Build > Build APK(s).

## Uso
1. Conceda "Acesso a todos os arquivos".
2. Toque em Ligar.
3. Deixe o app em segundo plano (notificação fixa).
4. Abra o WhatsApp normalmente. As mídias aparecem na grade.

## Baixar
- Toque longo em um item > "Baixar para o aparelho".
- Ou use "Baixar tudo" para exportar a lista filtrada de uma vez.
- Imagens vão para Pictures/VaultView e vídeos para Movies/VaultView,
  aparecendo na galeria. Áudios e outros formatos vão para Download/VaultView.
- O arquivo original continua dentro do app; o download é uma cópia.

## Como a classificação funciona
Arquivos que aparecem nas pastas do WhatsApp e não são indexados na galeria
são marcados como "única".

## Limites
- Só captura do momento em que o serviço é ligado em diante.
- Se você desativar "visibilidade de mídia" no WhatsApp, tudo parece única.
- O APK de debug é assinado com chave de debug e serve para uso pessoal.