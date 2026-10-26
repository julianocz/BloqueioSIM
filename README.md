# Bloqueio por SIM

App Android (sem bibliotecas externas) que rejeita ligações de números **fora dos seus contatos**, apenas no chip escolhido (SIM 1, SIM 2 ou os dois).

Requer Android 10 ou superior.

## Como gerar o APK pelo celular (GitHub)
1. Crie uma conta em github.com e um repositório novo (pode ser privado).
2. Envie todos os arquivos desta pasta (mantendo as pastas, incluindo `.github`).
3. Abra a aba **Actions** → "Gerar APK" → aguarde ~3 min.
4. Baixe o arquivo **BloqueioSIM-apk** (vem em .zip; dentro está o `app-debug.apk`).
5. Instale no celular (autorize "instalar apps desconhecidos").

## Como gerar pelo computador
Abra a pasta no Android Studio → Build → Build APK.

## Uso
1. Abra o app → **Conceder permissões** (contatos e telefone).
2. **Definir como filtro de chamadas** → aceite.
3. Ative o bloqueio e marque o(s) chip(s).

As chamadas bloqueadas são rejeitadas sem tocar, continuam no histórico do telefone e aparecem na lista dentro do app.

## Observações
- Só um app pode ser o "filtro de chamadas" por vez (se usar outro bloqueador, ele será substituído).
- Se faltar a permissão de contatos, o app não bloqueia nada (por segurança).
- Alguns aparelhos (Xiaomi, por ex.) podem exigir liberar o app na economia de bateria.
