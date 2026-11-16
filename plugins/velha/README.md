# Plugin Jogo da Velha (1x1)

Plugin para o **pitiupi** em que dois usuários da sala jogam uma partida de Jogo da Velha, um contra o outro. Toda a comunicação do plugin usa **UDP multicast** (`TransportMode.MULTICAST_UDP`, grupo da sala), enviada pelo `MessageDispatcher` do núcleo.

- **Autor:** Bernardo Motta
- **Identificador:** `pitiupi.velha`
- **Disciplina:** Redes de Computadores (UFSJ)

## Por que multicast

Quem desafia não sabe quem está disponível para jogar, então o `DESAFIO` precisa chegar à sala inteira. Com multicast, ele é enviado **uma única vez** e todos os membros do grupo recebem. Com unicast, seria preciso mandar uma cópia para cada pessoa online. Depois que a partida começa, as mensagens continuam indo pelo mesmo grupo, e cada instância filtra pelo `partidaId`: só os dois jogadores tratam as mensagens da partida, e os outros as descartam. Assim, o plugin usa um único canal e um único formato de mensagem do início ao fim.

## Como usar

1. Abra a aba **Jogo da Velha** na galeria de plugins.
2. Clique em **Desafiar a sala**. Também dá para clicar numa pessoa na lista lateral e escolher **Jogo da Velha → Desafiar para Jogo da Velha**, o que faz um desafio só para ela.
3. Do outro lado, o desafio aparece em **Desafios abertos**. Selecione-o e clique em **Aceitar desafio**.
4. Quem desafiou é o **X** e começa. Clique numa casa vazia na sua vez.
5. O botão **Desistir** encerra a partida, e o adversário vence.

## Protocolo

### Formato da mensagem

O plugin tem uma única classe de mensagem, `pitiupi.velha.net.VelhaMessage`, que estende `pitiupi.models.Messages.Message`. Ela é serializada pelo núcleo com a serialização Java e enviada num datagrama UDP para o grupo multicast da sala. O campo `tipo` define o significado.

| Campo | Tipo | Uso |
| --- | --- | --- |
| `tipo` | enum | `DESAFIO`, `ACEITE`, `INICIO`, `JOGADA` ou `DESISTENCIA` |
| `partidaId` | String (UUID) | Identifica a partida. Várias partidas podem acontecer na mesma sala |
| `remetenteId` | String (UUID) | Id aleatório da instância do plugin que enviou. Usado para ignorar o eco das próprias mensagens multicast e para saber quem é X e quem é O |
| `remetenteNome` | String | Nome do usuário na sala |
| `alvoNome` | String | Só no `DESAFIO`: nome de quem foi convidado. `null` = desafio para a sala toda |
| `jogadorXId`, `jogadorXNome`, `jogadorOId`, `jogadorONome` | String | Jogadores da partida (em `INICIO`, `JOGADA` e `DESISTENCIA`) |
| `seq` | int | Número de jogadas já feitas na partida (0 a 9) |
| `posicao` | int | Casa jogada, de 0 a 8 (`linha * 3 + coluna`); `-1` se não se aplica |
| `tabuleiro` | String (9 caracteres) | Tabuleiro completo depois da jogada: `X`, `O` ou `.`, da casa 0 à 8 |

Numeração das casas:

```
 0 | 1 | 2
---+---+---
 3 | 4 | 5
---+---+---
 6 | 7 | 8
```

### Tipos de mensagem

| Tipo | Quem envia | Quando | Efeito em quem recebe |
| --- | --- | --- | --- |
| `DESAFIO` | Desafiante | Ao clicar em desafiar, e de novo a cada **1,5 s** enquanto ninguém aceita | Entra (ou é renovado) na lista de desafios abertos. Se `alvoNome` é de outra pessoa, é ignorado |
| `ACEITE` | Quem quer jogar | Ao clicar em aceitar, e de novo a cada 1,5 s até chegar o `INICIO` | Só o desafiante trata. O **primeiro** aceite que chega define o adversário |
| `INICIO` | Desafiante | Quando escolhe o adversário | Tira o desafio da lista de todos. O escolhido vira O; quem aceitou e não foi escolhido é avisado de que o desafio já tem adversário |
| `JOGADA` | Jogador da vez | A cada jogada | O adversário atualiza o tabuleiro. Quem não é da partida descarta |
| `DESISTENCIA` | Um jogador | Ao clicar em desistir | A partida termina e o outro jogador vence |

### Sequência de uma partida

```
 Ana (desafiante)               grupo multicast                 Bia
      |-- DESAFIO (a cada 1,5 s) ---->|---------------------------->|
      |                               |<------------- ACEITE -------|
      |-- INICIO (X=Ana, O=Bia) ----->|---------------------------->|
      |-- JOGADA seq=1 pos=4 -------->|---------------------------->|
      |<------------------------------|<--- JOGADA seq=2 pos=0 -----|
      |-- JOGADA seq=3 ... ---------->|---------------------------->|
      |              (alternando até vitória, empate ou desistência)|
```

### Regras de validação

Cada jogador aplica as mesmas regras às mensagens que recebe:

1. **Eco:** uma mensagem com `remetenteId` igual ao meu é descartada, porque o multicast também entrega o datagrama a quem enviou.
2. **Partida:** uma `JOGADA` ou `DESISTENCIA` cujo `partidaId` não é o da minha partida é descartada.
3. **Turno:** X faz as jogadas ímpares (`seq` 1, 3, 5, 7, 9) e O as pares. Uma `JOGADA` só é aceita se o `remetenteId` for o jogador que deveria ter feito aquela jogada. Isso impede que o adversário jogue fora da vez ou que um terceiro interfira.
4. **Ordem:** a jogada só é aceita se `seq` for **maior** que o número de jogadas que eu já conheço. Duplicatas (reenvios) e datagramas atrasados são descartados.
5. **Consistência:** o `tabuleiro` recebido precisa manter todas as marcas que já existem localmente e ter exatamente `ceil(seq/2)` X e `floor(seq/2)` O.
6. **Fim de jogo:** cada instância calcula sozinha se houve vitória (três em linha) ou empate (`seq = 9`, que dá "velha"). Como as regras são determinísticas, os dois jogadores chegam ao mesmo resultado sem uma mensagem extra de "fim".

### Confiabilidade sobre UDP

UDP não garante entrega nem ordem. O protocolo trata isso de três formas:

- **Estado completo em cada jogada:** a `JOGADA` leva o tabuleiro inteiro e os dois jogadores, não só a casa jogada. Se um datagrama se perde, o próximo já contém aquela jogada. Se o adversário perdeu o `INICIO`, a primeira `JOGADA` que ele receber já basta para montar a partida.
- **Reenvio até a confirmação implícita:** quem acabou de jogar repete a mesma `JOGADA` a cada 1,5 s até receber a jogada seguinte do adversário. A resposta do adversário é a confirmação (ack implícito). O `ACEITE` é repetido até chegar o `INICIO`, e se o desafiante recebe de novo o aceite do adversário escolhido, ele reenvia o `INICIO`. Depois que a partida termina, a última mensagem é repetida mais 3 vezes, porque não haverá próxima jogada para confirmar.
- **Desafio como estado leve (soft state):** o desafio aberto não tem mensagem de cancelamento. O desafiante reanuncia o `DESAFIO` a cada 1,5 s, e quem recebe apaga da lista o desafio que não for renovado em 5 s. Se o desafiante cancelar, fechar o programa ou cair da rede, o convite some sozinho.

Corrida entre dois aceites: se duas pessoas aceitam ao mesmo tempo, vale o primeiro `ACEITE` que o desafiante receber. O `INICIO` vai para a sala inteira com o nome de O, e quem também aceitou vê que não foi escolhido.

### Limitações conhecidas

- O desafio direcionado usa o **nome** do usuário (`alvoNome`), então os nomes na sala precisam ser diferentes. O README do pitiupi já recomenda isso.
- Não há autenticação: o `remetenteId` é só um identificador aleatório. Num ambiente hostil, outra pessoa poderia forjar mensagens.
- O multicast depende de a rede local permitir o grupo da sala (`224.0.0.3`). Em redes que bloqueiam multicast, o plugin não funciona.

## Estrutura do código

```
plugins/velha/
├── build.xml, nbproject/                       build Ant (mesmo modelo do plugin chat)
└── src/
    ├── META-INF/services/pitiupi.models.Plugin  registro SPI → pitiupi.velha.plugin.VelhaPlugin
    └── pitiupi/velha/
        ├── plugin/VelhaPlugin.java      ponto de entrada: createPlugin, getMessages, receiveMessage, peer actions
        ├── net/VelhaMessage.java        a mensagem do protocolo
        ├── control/Partida.java         regras do jogo (turno, validação, vitória/empate), sem rede nem Swing
        ├── control/VelhaController.java máquina de estados do protocolo, envio e reenvio
        └── GUI/VelhaPanel.java          aba central: tabuleiro, desafios abertos e registro de eventos
```

`receiveMessage` é chamado pelo núcleo numa thread de rede. O `VelhaController` é `synchronized` e a tela só é alterada pela thread do Swing (`SwingUtilities.invokeLater`).

## Como compilar e rodar

Na raiz do projeto:

```bash
# Linux / WSL
./scripts/pitiupi.sh run --all
```

```bat
:: Windows
scripts\pitiupi.cmd run --all
```

O plugin está registrado em `plugins.registry.json` com o id `velha`, então o script compila e instala o JAR `dist/pitiupi.velha.jar` junto com os outros.

Para testar, abra o pitiupi em dois computadores da mesma rede, com a mesma porta e nomes diferentes.
