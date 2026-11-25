package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class ConexaoPostgres {
    private static final Path ARQUIVO_CONFIGURACAO =
            Paths.get("src", "dados", "database.properties");

    private ConexaoPostgres() {
    }

    public static Connection conectar() throws SQLException {
        Properties propriedades = new Properties();

        try (InputStream entrada = Files.newInputStream(ARQUIVO_CONFIGURACAO)) {
            propriedades.load(entrada);
        } catch (IOException erro) {
            throw new SQLException(
                    "Arquivo src/dados/database.properties nao encontrado ou ilegivel.",
                    erro
            );
        }

        String url = obterPropriedade(propriedades, "db.url");
        String usuario = obterPropriedade(propriedades, "db.user");
        String senha = obterPropriedade(propriedades, "db.password");

        return DriverManager.getConnection(url, usuario, senha);
    }

    public static void inicializarBanco() throws SQLException {
        Path script = Paths.get("src", "dados", "biblioteca.sql");
        String sql;
        try {
            sql = Files.readString(script, StandardCharsets.UTF_8);
        } catch (IOException erro) {
            throw new SQLException("Nao foi possivel ler src/dados/biblioteca.sql.", erro);
        }

        try (Connection conexao = conectar();
             Statement comando = conexao.createStatement()) {
            for (String instruicao : sql.split(";")) {
                String limpa = instruicao.trim();
                if (!limpa.isEmpty()) {
                    comando.execute(limpa);
                }
            }
        }
    }

    private static String obterPropriedade(Properties propriedades, String chave)
            throws SQLException {
        String valor = propriedades.getProperty(chave);
        if (valor == null || valor.trim().isEmpty()) {
            throw new SQLException("Propriedade obrigatoria ausente: " + chave);
        }
        return valor.trim();
    }
}
