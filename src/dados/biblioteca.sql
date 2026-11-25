CREATE TABLE IF NOT EXISTS livros (
    codigo SERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    autor VARCHAR(255) NOT NULL,
    genero VARCHAR(100) NOT NULL,
    ano_publicacao INTEGER NOT NULL,
    disponivel BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS leitores (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    cpf VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS emprestimos (
    codigo SERIAL PRIMARY KEY,
    codigo_livro INTEGER NOT NULL REFERENCES livros(codigo),
    id_leitor INTEGER NOT NULL REFERENCES leitores(id),
    data_emprestimo DATE NOT NULL,
    data_prevista_devolucao DATE NOT NULL,
    devolvido BOOLEAN NOT NULL DEFAULT FALSE
);
