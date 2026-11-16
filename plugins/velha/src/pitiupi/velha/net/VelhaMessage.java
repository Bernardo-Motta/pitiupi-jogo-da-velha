package pitiupi.velha.net;

import pitiupi.models.Messages.Message;

/**
 * Única mensagem do protocolo do Jogo da Velha.
 *
 * <p>Todas as mensagens vão por UDP multicast para a sala inteira. O campo
 * {@link #tipo} diz o que a mensagem significa. Ver o README do plugin para a
 * descrição completa do protocolo.</p>
 */
public class VelhaMessage extends Message {

    private static final long serialVersionUID = 1L;

    /** Tipos de mensagem do protocolo. */
    public enum Tipo {
        /** Convite para uma partida. Reanunciado a cada 1,5 s enquanto ninguém aceita. */
        DESAFIO,
        /** Resposta de quem quer jogar o desafio. */
        ACEITE,
        /** O desafiante escolheu o adversário: a partida começou. */
        INICIO,
        /** Uma jogada, com o tabuleiro inteiro depois dela. */
        JOGADA,
        /** Um jogador abandonou a partida. */
        DESISTENCIA
    }

    private Tipo tipo;
    private String partidaId;

    /** Id aleatório da instância do plugin que enviou (não é o IP). */
    private String remetenteId;
    private String remetenteNome;

    /** Só no DESAFIO: nome do adversário convidado, ou null para a sala inteira. */
    private String alvoNome;

    /** Jogadores da partida (INICIO, JOGADA e DESISTENCIA). X sempre começa. */
    private String jogadorXId;
    private String jogadorXNome;
    private String jogadorOId;
    private String jogadorONome;

    /** Número de jogadas já feitas (1 a 9) depois desta mensagem. */
    private int seq;
    /** Casa jogada, de 0 a 8 (linha * 3 + coluna). -1 quando não se aplica. */
    private int posicao = -1;
    /** Tabuleiro com 9 caracteres: 'X', 'O' ou '.', da casa 0 à casa 8. */
    private String tabuleiro;

    public VelhaMessage() {
    }

    public VelhaMessage(Tipo tipo, String partidaId, String remetenteId, String remetenteNome) {
        this.tipo = tipo;
        this.partidaId = partidaId;
        this.remetenteId = remetenteId;
        this.remetenteNome = remetenteNome;
    }

    public Tipo getTipo() { return tipo; }
    public String getPartidaId() { return partidaId; }
    public String getRemetenteId() { return remetenteId; }
    public String getRemetenteNome() { return remetenteNome; }

    public String getAlvoNome() { return alvoNome; }
    public void setAlvoNome(String alvoNome) { this.alvoNome = alvoNome; }

    public String getJogadorXId() { return jogadorXId; }
    public String getJogadorXNome() { return jogadorXNome; }
    public String getJogadorOId() { return jogadorOId; }
    public String getJogadorONome() { return jogadorONome; }

    public void setJogadores(String xId, String xNome, String oId, String oNome) {
        this.jogadorXId = xId;
        this.jogadorXNome = xNome;
        this.jogadorOId = oId;
        this.jogadorONome = oNome;
    }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public int getPosicao() { return posicao; }
    public void setPosicao(int posicao) { this.posicao = posicao; }

    public String getTabuleiro() { return tabuleiro; }
    public void setTabuleiro(String tabuleiro) { this.tabuleiro = tabuleiro; }
}
