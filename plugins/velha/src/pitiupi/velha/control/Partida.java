package pitiupi.velha.control;

import pitiupi.velha.net.VelhaMessage;

/**
 * Estado de uma partida: jogadores, tabuleiro e número de jogadas.
 * Não conhece rede nem Swing.
 */
public class Partida {

    private final String id;
    private final String xId;
    private final String xNome;
    private final String oId;
    private final String oNome;

    private char[] tabuleiro = ".........".toCharArray();
    private int seq;
    /** Preenchido quando alguém desiste: 'X' ou 'O' é quem venceu. */
    private char vencedorPorDesistencia;

    public Partida(String id, String xId, String xNome, String oId, String oNome) {
        this.id = id;
        this.xId = xId;
        this.xNome = xNome;
        this.oId = oId;
        this.oNome = oNome;
    }

    public static Partida from(VelhaMessage m) {
        return new Partida(m.getPartidaId(), m.getJogadorXId(), m.getJogadorXNome(),
                m.getJogadorOId(), m.getJogadorONome());
    }

    public String getId() { return id; }
    public String getXId() { return xId; }
    public String getXNome() { return xNome; }
    public String getOId() { return oId; }
    public String getONome() { return oNome; }
    public int getSeq() { return seq; }

    public char casa(int i) {
        return tabuleiro[i];
    }

    public String tabuleiroTexto() {
        return new String(tabuleiro);
    }

    /** Símbolo de quem faz a próxima jogada. X joga nas jogadas ímpares (1, 3, 5...). */
    public char vez() {
        return seq % 2 == 0 ? 'X' : 'O';
    }

    public String idDoSimbolo(char simbolo) {
        return simbolo == 'X' ? xId : oId;
    }

    public String nomeDoSimbolo(char simbolo) {
        return simbolo == 'X' ? xNome : oNome;
    }

    /** 'X' ou 'O' se o id for de um jogador desta partida, senão 0. */
    public char simboloDe(String jogadorId) {
        if (jogadorId == null) {
            return 0;
        }
        if (jogadorId.equals(xId)) {
            return 'X';
        }
        if (jogadorId.equals(oId)) {
            return 'O';
        }
        return 0;
    }

    /** Aplica uma jogada local. Devolve false se a casa estiver ocupada ou a partida acabou. */
    public boolean jogar(int posicao) {
        if (encerrada() || posicao < 0 || posicao > 8 || tabuleiro[posicao] != '.') {
            return false;
        }
        tabuleiro[posicao] = vez();
        seq++;
        return true;
    }

    /**
     * Aceita o estado que chegou pela rede.
     *
     * <p>Regras: o número da jogada tem de ser maior que o local (duplicatas e
     * mensagens atrasadas são descartadas), o tabuleiro recebido tem de conter
     * todas as marcas que já existem aqui, e a contagem de X e O tem de bater
     * com o número da jogada. Se uma jogada intermediária se perdeu, este estado
     * mais novo já a contém, então a partida se recupera sozinha.</p>
     */
    public boolean aceitarEstado(int novoSeq, String novoTabuleiro) {
        if (encerrada() || novoSeq <= seq || novoSeq > 9
                || novoTabuleiro == null || novoTabuleiro.length() != 9) {
            return false;
        }
        int xs = 0;
        int os = 0;
        for (int i = 0; i < 9; i++) {
            char c = novoTabuleiro.charAt(i);
            if (c == 'X') {
                xs++;
            } else if (c == 'O') {
                os++;
            } else if (c != '.') {
                return false;
            }
            if (tabuleiro[i] != '.' && tabuleiro[i] != c) {
                return false;
            }
        }
        if (xs != (novoSeq + 1) / 2 || os != novoSeq / 2) {
            return false;
        }
        tabuleiro = novoTabuleiro.toCharArray();
        seq = novoSeq;
        return true;
    }

    public void registrarDesistencia(char quemDesistiu) {
        if (!encerrada()) {
            vencedorPorDesistencia = quemDesistiu == 'X' ? 'O' : 'X';
        }
    }

    private static final int[][] LINHAS = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    /** 'X' ou 'O' para vencedor, 'E' para empate, 0 se a partida continua. */
    public char resultado() {
        if (vencedorPorDesistencia != 0) {
            return vencedorPorDesistencia;
        }
        for (int[] l : LINHAS) {
            char c = tabuleiro[l[0]];
            if (c != '.' && c == tabuleiro[l[1]] && c == tabuleiro[l[2]]) {
                return c;
            }
        }
        return seq == 9 ? 'E' : 0;
    }

    public boolean encerrada() {
        return resultado() != 0;
    }

    public boolean terminouPorDesistencia() {
        return vencedorPorDesistencia != 0;
    }

    public String descricaoResultado() {
        char r = resultado();
        if (r == 0) {
            return "Vez de " + nomeDoSimbolo(vez()) + " (" + vez() + ")";
        }
        if (r == 'E') {
            return "Deu velha! Empate.";
        }
        String texto = nomeDoSimbolo(r) + " (" + r + ") venceu";
        return terminouPorDesistencia() ? texto + " por desistência." : texto + "!";
    }
}
