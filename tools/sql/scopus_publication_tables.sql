-- OPENSCIENCE-USS
-- Tablas de publicaciones Scopus.
-- Ejecutar manualmente antes de CargarPublicacionesScopusCsv.
-- Este script NO crea ni modifica scopus_author, scopus_areas
-- ni scopus_author_affiliation.

CREATE TABLE IF NOT EXISTS scopus_publication (
    id BIGINT NOT NULL AUTO_INCREMENT,
    eid VARCHAR(64) NULL,
    doi VARCHAR(255) NULL,
    doi_normalized VARCHAR(255) NULL,
    title TEXT NULL,
    publication_year INT NULL,
    source_title VARCHAR(1000) NULL,
    volume VARCHAR(100) NULL,
    issue VARCHAR(100) NULL,
    article_number VARCHAR(100) NULL,
    page_start VARCHAR(50) NULL,
    page_end VARCHAR(50) NULL,
    cited_by INT NULL,
    link TEXT NULL,
    issn VARCHAR(50) NULL,
    isbn text NULL,
    coden VARCHAR(50) NULL,
    pubmed_id VARCHAR(50) NULL,
    document_type VARCHAR(100) NULL,
    publication_stage VARCHAR(100) NULL,
    open_access TEXT NULL,
    source VARCHAR(100) NULL,
    authors_raw TEXT NULL,
    author_full_names_raw longtext NULL,
    author_ids_raw TEXT NULL,
    affiliations_raw LONGTEXT NULL,
    authors_with_affiliations_raw LONGTEXT NULL,
    author_keywords_raw TEXT NULL,
    index_keywords_raw TEXT NULL,
    funding_details LONGTEXT NULL,
    funding_texts LONGTEXT NULL,
    `abstract` LONGTEXT NULL,
    correspondence_address TEXT NULL,
    editors TEXT NULL,
    source_file VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scopus_publication_eid (eid),
    KEY idx_scopus_publication_doi (doi_normalized)
);

CREATE TABLE IF NOT EXISTS scopus_publication_author (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scopus_publication_id BIGINT NOT NULL,
    author_position INT NOT NULL,
    auth_id VARCHAR(30) NULL,
    author_name_short VARCHAR(255) NULL,
    author_name_full VARCHAR(500) NULL,
    author_affiliations_raw TEXT NULL,
    scopus_author_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scopus_publication_author_pos (scopus_publication_id, author_position),
    KEY idx_scopus_publication_author_auth_id (auth_id),
    KEY idx_scopus_publication_author_scopus_author (scopus_author_id),
    CONSTRAINT fk_scopus_publication_author_publication
        FOREIGN KEY (scopus_publication_id) REFERENCES scopus_publication (id)
);

CREATE TABLE IF NOT EXISTS scopus_publication_affiliation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scopus_publication_id BIGINT NOT NULL,
    affiliation_position INT NOT NULL,
    affiliation_text TEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scopus_publication_affiliation_pos (scopus_publication_id, affiliation_position),
    CONSTRAINT fk_scopus_publication_affiliation_publication
        FOREIGN KEY (scopus_publication_id) REFERENCES scopus_publication (id)
);

CREATE TABLE IF NOT EXISTS scopus_publication_keyword (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scopus_publication_id BIGINT NOT NULL,
    keyword_type VARCHAR(20) NOT NULL,
    keyword_position INT NOT NULL,
    keyword_text VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scopus_publication_keyword_pos (scopus_publication_id, keyword_type, keyword_position),
    CONSTRAINT fk_scopus_publication_keyword_publication
        FOREIGN KEY (scopus_publication_id) REFERENCES scopus_publication (id)
);
