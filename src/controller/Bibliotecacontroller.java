package controller;

import exception.EmprestimoException;
import exception.LeitorNaoEncontrado;
import exception.LivroNaoEncontrado;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import model.Emprestimo;
import model.Leitor;
import model.Livro;
import util.ArquivoTexto;
import util.ConexaoPostgres;

public class Bibliotecacontroller {
    private Map<Integer, Livro> livros;
    private Map<Integer, Leitor> leitores;
    private ArrayList<Emprestimo> emprestimos;
    private int proximoLivro;
    private int proximoLeitor;
    private int proximoEmprestimo;
    public Bibliotecacontroller() {
        livros = new HashMap<>();
        leitores = new HashMap<>();
        emprestimos = new ArrayList<>();
        proximoLivro = 1;
        proximoLeitor = 1;
        proximoEmprestimo = 1;
    }
    public void cadastrarLivro(String titulo, String autor, String genero, int ano) {
        String sql = "INSERT INTO livro(titulo, autor, genero, anopublicacao) VALUES (?, ?, ?, ?) RETURNING codigo";
        try (Connection conexao = ConexaoPostgres.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, titulo);
            comando.setString(2, autor);
            comando.setString(3, genero);
            comando.setInt(4, ano);
            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    int codigo = resultado.getInt("codigo");
                    livros.put(codigo, new Livro(codigo, titulo, autor, genero, ano));
                    proximoLivro = codigo + 1;
                }
            }
        } catch (SQLException erro) {
            throw new RuntimeException("Erro ao cadastrar livro: " + erro.getMessage(), erro);
        }
    }
    public void cadastrarLeitor(String nome, String cpf) {
        String sql = "INSERT INTO leitores(nome, cpf) VALUES (?, ?) RETURNING id";
        try (Connection conexao = ConexaoPostgres.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, nome);
            comando.setString(2, cpf);
            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    int id = resultado.getInt("id");
                    leitores.put(id, new Leitor(id, nome, cpf));
                    proximoLeitor = id + 1;
                }
            }
        } catch (SQLException erro) {
            throw new RuntimeException("Erro ao cadastrar leitor: " + erro.getMessage(), erro);
        }
    }
    public Livro buscarLivro(int codigo) {
        return livros.get(codigo);
    }
    public Leitor buscarLeitor(int id) {
        return leitores.get(id);
    }
    public void listarLivros() {
        if(livros.isEmpty()){
            System.out.println("Nenhum livro cadastrado.");
            return;
        }
        for(Livro livro : livros.values()){
            System.out.println(livro);
        }
    }
    public void listarLeitores() {
        if(leitores.isEmpty()){
            System.out.println("Nenhum leitor cadastrado.");
            return;
        }
        for(Leitor leitor : leitores.values()){
            System.out.println(leitor);
        }
    }
    public boolean realizarEmprestimo(int codigoLivro, int idLeitor)
            throws LivroNaoEncontrado,
            LeitorNaoEncontrado,
            EmprestimoException {
                Livro livro = livros.get(codigoLivro);
                    if(livro == null){
                         throw new LivroNaoEncontrado();
                    }
                    Leitor leitor = leitores.get(idLeitor);
                    if(leitor == null){
                        throw new LeitorNaoEncontrado();
                    }
                    if(!livro.estaDisponivel()){
                        throw new EmprestimoException("Livro indisponível.");
                    }
                    LocalDate dataEmprestimo = LocalDate.now();
                    LocalDate dataDevolucao = dataEmprestimo.plusDays(7);
                    String sql = "INSERT INTO emprestimos(codigo_livro, id_leitor, data_emprestimo, data_prevista_devolucao) "
                            + "VALUES (?, ?, ?, ?) RETURNING codigo";
                    try (Connection conexao = ConexaoPostgres.conectar();
                         PreparedStatement comando = conexao.prepareStatement(sql);
                         PreparedStatement atualizaLivro = conexao.prepareStatement(
                                 "UPDATE livros SET disponivel = FALSE WHERE codigo = ?")) {
                        conexao.setAutoCommit(false);
                        comando.setInt(1, codigoLivro);
                        comando.setInt(2, idLeitor);
                        comando.setObject(3, dataEmprestimo);
                        comando.setObject(4, dataDevolucao);
                        try (ResultSet resultado = comando.executeQuery()) {
                            if (!resultado.next()) {
                                conexao.rollback();
                                throw new EmprestimoException("Nao foi possivel registrar o emprestimo.");
                            }
                            atualizaLivro.setInt(1, codigoLivro);
                            atualizaLivro.executeUpdate();
                            int codigo = resultado.getInt("codigo");
                            Emprestimo emprestimo = new Emprestimo(
                                    codigo, livro, leitor, dataEmprestimo, dataDevolucao);
                            livro.emprestar();
                            leitor.adicionarLivro(livro);
                            emprestimos.add(emprestimo);
                            proximoEmprestimo = codigo + 1;
                            conexao.commit();
                        } catch (EmprestimoException erro) {
                            conexao.rollback();
                            throw erro;
                        } catch (SQLException erro) {
                            conexao.rollback();
                            throw erro;
                        }
                    } catch (EmprestimoException erro) {
                        throw erro;
                    } catch (SQLException erro) {
                        throw new RuntimeException("Erro ao realizar emprestimo: " + erro.getMessage(), erro);
                    }
                    return true;
    }
    public boolean devolverLivro(int codigoEmprestimo){
        for(Emprestimo e : emprestimos){
            if(e.getCodigo() == codigoEmprestimo){
                if(e.isDevolvido()){
                    return false;
                }
                String sql = "UPDATE emprestimos SET devolvido = TRUE WHERE codigo = ?";
                try (Connection conexao = ConexaoPostgres.conectar();
                     PreparedStatement devolucao = conexao.prepareStatement(sql);
                     PreparedStatement livro = conexao.prepareStatement(
                             "UPDATE livros SET disponivel = TRUE WHERE codigo = ?")) {
                    conexao.setAutoCommit(false);
                    devolucao.setInt(1, codigoEmprestimo);
                    devolucao.executeUpdate();
                    livro.setInt(1, e.getLivro().getCodigo());
                    livro.executeUpdate();
                    conexao.commit();
                    e.finalizarEmprestimo();
                    e.getLeitor().removerLivro(e.getLivro());
                    return true;
                } catch (SQLException erro) {
                    throw new RuntimeException("Erro ao devolver livro: " + erro.getMessage(), erro);
                }
            }
        }
        return false;
    }
    public void listarEmprestimos() {
        if (emprestimos.isEmpty()) {
            System.out.println("Nenhum empréstimo registrado.");
            return;
        }
        for (Emprestimo e : emprestimos) {
            System.out.println(e);
        }
    }
    public boolean removerLivro(int codigo){
        Livro livro = livros.get(codigo);
        if(livro == null){
            return false;
        }
        else if(!livro.estaDisponivel()){
            return false;
        }
        try (Connection conexao = ConexaoPostgres.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "DELETE FROM livros WHERE codigo = ?")) {
            comando.setInt(1, codigo);
            if (comando.executeUpdate() == 1) {
                livros.remove(codigo);
                return true;
            }
            return false;
        } catch (SQLException erro) {
            throw new RuntimeException("Erro ao remover livro: " + erro.getMessage(), erro);
        }
    }
    public boolean removerLeitor(int id){
        Leitor leitor = leitores.get(id);
        if(leitor == null){
            return false;
        }
        if(!leitor.getLivrosEmprestados().isEmpty()){
            return false;
        }
        try (Connection conexao = ConexaoPostgres.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "DELETE FROM leitores WHERE id = ?")) {
            comando.setInt(1, id);
            if (comando.executeUpdate() == 1) {
                leitores.remove(id);
                return true;
            }
            return false;
        } catch (SQLException erro) {
            throw new RuntimeException("Erro ao remover leitor: " + erro.getMessage(), erro);
        }
    }
    public Map<Integer, Livro> getLivros() {
        return livros;
    }
    public Map<Integer, Leitor> getLeitores() {
        return leitores;
    }
    public ArrayList<Emprestimo> getEmprestimos() {
        return emprestimos;
    }
    public void salvarDados(){
        carregarDados();
    }
    public void carregarDados(){
        try {
            ConexaoPostgres.inicializarBanco();
            livros.clear();
            leitores.clear();
            emprestimos.clear();

            try (Connection conexao = ConexaoPostgres.conectar()) {
                try (Statement comando = conexao.createStatement();
                     ResultSet resultado = comando.executeQuery(
                             "SELECT codigo, titulo, autor, genero, anopublicacao, disponivel FROM livro")) {
                    while (resultado.next()) {
                        Livro livro = new Livro(
                                resultado.getInt("codigo"),
                                resultado.getString("titulo"),
                                resultado.getString("autor"),
                                resultado.getString("genero"),
                                resultado.getInt("anopublicacao"));
                        if (!resultado.getBoolean("disponivel")) {
                            livro.emprestar();
                        }
                        livros.put(livro.getCodigo(), livro);
                    }
                }

                try (Statement comando = conexao.createStatement();
                     ResultSet resultado = comando.executeQuery(
                             "SELECT id, nome, cpf FROM leitores")) {
                    while (resultado.next()) {
                        Leitor leitor = new Leitor(
                                resultado.getInt("id"),
                                resultado.getString("nome"),
                                resultado.getString("cpf"));
                        leitores.put(leitor.getId(), leitor);
                    }
                }

                String sql = "SELECT e.codigo, e.codigo_livro, e.id_leitor, "
                        + "e.data_emprestimo, e.data_prevista_devolucao, e.devolvido "
                        + "FROM emprestimos e";
                try (Statement comando = conexao.createStatement();
                     ResultSet resultado = comando.executeQuery(sql)) {
                    while (resultado.next()) {
                        Livro livro = livros.get(resultado.getInt("codigo_livro"));
                        Leitor leitor = leitores.get(resultado.getInt("id_leitor"));
                        if (livro == null || leitor == null) {
                            continue;
                        }
                        Emprestimo emprestimo = new Emprestimo(
                                resultado.getInt("codigo"),
                                livro,
                                leitor,
                                resultado.getObject("data_emprestimo", LocalDate.class),
                                resultado.getObject("data_prevista_devolucao", LocalDate.class));
                        if (resultado.getBoolean("devolvido")) {
                            emprestimo.finalizarEmprestimo();
                        } else {
                            leitor.adicionarLivro(livro);
                        }
                        emprestimos.add(emprestimo);
                    }
                }
            }
        } catch (SQLException erro) {
            throw new RuntimeException("Erro ao carregar dados do PostgreSQL: " + erro.getMessage(), erro);
        }
    }
    public void gerarRelatorio(){
        ArquivoTexto.gerarRelatorio(livros, leitores, emprestimos);
    }
    public int quantidadeLivros(){
        return livros.size();
    }
    public int quantidadeLeitores(){
        return leitores.size();
    }
    public int quantidadeEmprestimos(){
        return emprestimos.size();
    }
    public int livrosDisponiveis(){
        int contador = 0;
        for(Livro livro : livros.values()){
            if(livro.estaDisponivel()){
                contador++;
            }
        }
        return contador;
    }
    public int livrosEmprestados(){
        return quantidadeLivros() - livrosDisponiveis();
    }
}