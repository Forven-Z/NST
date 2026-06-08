-- 智慧云脑诊疗平台 — 业务库 DDL
-- PostgreSQL 15+
-- 依据：docs/DATABASE_DESIGN.md v1.14
-- 用法：psql -U postgres -d hospital -f docs/sql/schema.sql

SET client_encoding = 'UTF8';
SET timezone = 'Asia/Shanghai';

-- =============================================================================
-- A. 基础字典
-- =============================================================================

CREATE TABLE IF NOT EXISTS department (
    id              BIGSERIAL PRIMARY KEY,
    dept_code       VARCHAR(32)  NOT NULL UNIQUE,
    dept_name       VARCHAR(64)  NOT NULL,
    dept_type       SMALLINT,
    parent_id       BIGINT REFERENCES department(id),
    sort_no         INTEGER      DEFAULT 0,
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS regist_level (
    id              BIGSERIAL PRIMARY KEY,
    level_code      VARCHAR(32)  NOT NULL UNIQUE,
    level_name      VARCHAR(32)  NOT NULL,
    regist_fee      NUMERIC(10,2) NOT NULL,
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settle_category (
    id              BIGSERIAL PRIMARY KEY,
    category_code   VARCHAR(32)  NOT NULL UNIQUE,
    category_name   VARCHAR(32)  NOT NULL,
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS employee (
    id              BIGSERIAL PRIMARY KEY,
    emp_no          VARCHAR(32)  NOT NULL UNIQUE,
    real_name       VARCHAR(64)  NOT NULL,
    gender          SMALLINT,
    dept_id         BIGINT       NOT NULL REFERENCES department(id),
    title           VARCHAR(32),
    role_type       VARCHAR(32)  NOT NULL,
    phone           VARCHAR(20),
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_employee_role_type ON employee(role_type);
CREATE INDEX IF NOT EXISTS ix_employee_dept_id ON employee(dept_id);

-- 出诊科室经 employee.dept_id 推导；作废用 publish_status=2（无 delmark）
CREATE TABLE IF NOT EXISTS scheduling (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employee(id),
    regist_level_id BIGINT       NOT NULL REFERENCES regist_level(id),
    work_date       DATE         NOT NULL,
    noon_type       SMALLINT     NOT NULL,
    total_quota     INTEGER      NOT NULL DEFAULT 0,
    used_quota      INTEGER      NOT NULL DEFAULT 0,
    publish_status  SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_scheduling_work_date ON scheduling(work_date, employee_id, noon_type);
CREATE INDEX IF NOT EXISTS ix_scheduling_publish_work_date ON scheduling(publish_status, work_date);
CREATE UNIQUE INDEX IF NOT EXISTS ux_scheduling_active_slot
    ON scheduling (work_date, employee_id, noon_type, regist_level_id)
    WHERE publish_status <> 2;

CREATE TABLE IF NOT EXISTS drug_info (
    id              BIGSERIAL PRIMARY KEY,
    drug_code       VARCHAR(32)  NOT NULL UNIQUE,
    drug_name       VARCHAR(128) NOT NULL,
    drug_format     VARCHAR(255),
    drug_dosage     VARCHAR(64),
    drug_type       VARCHAR(64),
    unit            VARCHAR(16),
    retail_price    NUMERIC(10,2) NOT NULL,
    manufacturer    VARCHAR(128),
    stock_qty       INTEGER      DEFAULT 0,
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_drug_info_name ON drug_info(drug_name);
CREATE INDEX IF NOT EXISTS ix_drug_info_drug_type ON drug_info(drug_type);

CREATE TABLE IF NOT EXISTS disease (
    id              BIGSERIAL PRIMARY KEY,
    disease_code    VARCHAR(32)  NOT NULL UNIQUE,
    disease_name    VARCHAR(128) NOT NULL,
    disease_category VARCHAR(64),
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_disease_name ON disease(disease_name);

CREATE TABLE IF NOT EXISTS medical_technology (
    id              BIGSERIAL PRIMARY KEY,
    item_code       VARCHAR(32)  NOT NULL UNIQUE,
    item_name       VARCHAR(128) NOT NULL,
    tech_type       VARCHAR(16)  NOT NULL,
    price           NUMERIC(10,2) NOT NULL,
    dept_id         BIGINT REFERENCES department(id),
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_medical_technology_type ON medical_technology(tech_type);

-- =============================================================================
-- B. 患者与认证
-- =============================================================================

CREATE TABLE IF NOT EXISTS patient (
    id                  BIGSERIAL PRIMARY KEY,
    medical_record_no   VARCHAR(32)  NOT NULL UNIQUE,
    real_name           VARCHAR(64)  NOT NULL,
    gender              SMALLINT,
    birth_date          DATE,
    age                 SMALLINT,
    id_card             VARCHAR(18),
    phone               VARCHAR(20),
    address             VARCHAR(256),
    settle_category_id  BIGINT REFERENCES settle_category(id),
    need_medical_book   BOOLEAN      NOT NULL DEFAULT FALSE,
    delmark             SMALLINT     NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_patient_real_name ON patient(real_name);
CREATE INDEX IF NOT EXISTS ix_patient_phone ON patient(phone);
CREATE INDEX IF NOT EXISTS ix_patient_id_card ON patient(id_card);

CREATE TABLE IF NOT EXISTS patient_wechat (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT       NOT NULL UNIQUE REFERENCES patient(id),
    openid          VARCHAR(64)  NOT NULL UNIQUE,
    unionid         VARCHAR(64),
    session_key     VARCHAR(128),
    last_login_time TIMESTAMPTZ,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 小程序家属就诊人（扩展表；已纳入 DATABASE_DESIGN §1.3 B′）
CREATE TABLE IF NOT EXISTS patient_family_link (
    id                  BIGSERIAL PRIMARY KEY,
    owner_patient_id    BIGINT       NOT NULL REFERENCES patient(id),
    member_patient_id   BIGINT       NOT NULL REFERENCES patient(id),
    relation_type       SMALLINT     NOT NULL DEFAULT 4,
    delmark             SMALLINT     NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_patient_id, member_patient_id)
);
CREATE INDEX IF NOT EXISTS ix_patient_family_owner ON patient_family_link(owner_patient_id);
COMMENT ON COLUMN patient_family_link.relation_type IS '0本人 1父母 2配偶 3子女 4其他';

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,
    employee_id     BIGINT UNIQUE REFERENCES employee(id),
    user_type       VARCHAR(16)  NOT NULL,
    status          SMALLINT     NOT NULL DEFAULT 1,
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- C. 挂号与病历
-- =============================================================================

CREATE TABLE IF NOT EXISTS register (
    id                  BIGSERIAL PRIMARY KEY,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    scheduling_id       BIGINT REFERENCES scheduling(id),
    dept_id             BIGINT       NOT NULL REFERENCES department(id),
    employee_id         BIGINT REFERENCES employee(id),
    regist_level_id     BIGINT       NOT NULL REFERENCES regist_level(id),
    settle_category_id  BIGINT REFERENCES settle_category(id),
    visit_date          DATE         NOT NULL,
    noon_type           SMALLINT     NOT NULL,
    visit_state         SMALLINT     NOT NULL DEFAULT 1,
    channel             VARCHAR(16)  NOT NULL,
    regist_fee          NUMERIC(10,2) NOT NULL,
    registrar_id        BIGINT REFERENCES employee(id),
    call_time           TIMESTAMPTZ,
    visit_end_time      TIMESTAMPTZ,
    remark              VARCHAR(256),
    delmark             SMALLINT     NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_register_patient_id ON register(patient_id);
CREATE INDEX IF NOT EXISTS ix_register_visit_date ON register(visit_date);
CREATE INDEX IF NOT EXISTS ix_register_visit_state ON register(visit_state);

CREATE TABLE IF NOT EXISTS medical_record (
    id                  BIGSERIAL PRIMARY KEY,
    register_id         BIGINT       NOT NULL UNIQUE REFERENCES register(id),
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    doctor_id           BIGINT       NOT NULL REFERENCES employee(id),
    readme              TEXT,
    present             TEXT,
    present_treat       TEXT,
    history             TEXT,
    allergy             TEXT,
    physique            TEXT,
    diagnosis           TEXT,
    cure                TEXT,
    check_advice        TEXT,
    inspection_advice   TEXT,
    status              SMALLINT     NOT NULL DEFAULT 0,
    delmark             SMALLINT     NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_medical_record_patient_id ON medical_record(patient_id);

CREATE TABLE IF NOT EXISTS medical_record_disease (
    id                  BIGSERIAL PRIMARY KEY,
    medical_record_id   BIGINT       NOT NULL REFERENCES medical_record(id),
    disease_id          BIGINT       NOT NULL REFERENCES disease(id),
    disease_type        SMALLINT     DEFAULT 1,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (medical_record_id, disease_id)
);
CREATE INDEX IF NOT EXISTS ix_mrd_medical_record_id ON medical_record_disease(medical_record_id);

-- =============================================================================
-- D. 医技医嘱（检查 / 检验 / 处置）
-- =============================================================================

CREATE TABLE IF NOT EXISTS check_request (
    id                      BIGSERIAL PRIMARY KEY,
    register_id             BIGINT       NOT NULL REFERENCES register(id),
    patient_id              BIGINT       NOT NULL REFERENCES patient(id),
    medical_technology_id   BIGINT       NOT NULL REFERENCES medical_technology(id),
    doctor_id               BIGINT       NOT NULL REFERENCES employee(id),
    item_price              NUMERIC(10,2) NOT NULL,
    purpose                 VARCHAR(256),
    body_part               VARCHAR(64),
    remark                  VARCHAR(256),
    status                  SMALLINT     NOT NULL DEFAULT 10,
    order_time              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    executor_id             BIGINT REFERENCES employee(id),
    execute_time            TIMESTAMPTZ,
    result_input_id         BIGINT REFERENCES employee(id),
    result_time             TIMESTAMPTZ,
    result_text             TEXT,
    result_attachment       VARCHAR(512),
    from_ai                 BOOLEAN      NOT NULL DEFAULT FALSE,
    confirm_time            TIMESTAMPTZ,
    delmark                 SMALLINT     NOT NULL DEFAULT 0,
    create_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_check_request_register_id ON check_request(register_id);
CREATE INDEX IF NOT EXISTS ix_check_request_status ON check_request(status);

CREATE TABLE IF NOT EXISTS inspection_request (
    id                      BIGSERIAL PRIMARY KEY,
    register_id             BIGINT       NOT NULL REFERENCES register(id),
    patient_id              BIGINT       NOT NULL REFERENCES patient(id),
    medical_technology_id   BIGINT       NOT NULL REFERENCES medical_technology(id),
    doctor_id               BIGINT       NOT NULL REFERENCES employee(id),
    item_price              NUMERIC(10,2) NOT NULL,
    purpose                 VARCHAR(256),
    body_part               VARCHAR(64),
    remark                  VARCHAR(256),
    status                  SMALLINT     NOT NULL DEFAULT 10,
    order_time              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    executor_id             BIGINT REFERENCES employee(id),
    execute_time            TIMESTAMPTZ,
    result_input_id         BIGINT REFERENCES employee(id),
    result_time             TIMESTAMPTZ,
    result_text             TEXT,
    result_attachment       VARCHAR(512),
    from_ai                 BOOLEAN      NOT NULL DEFAULT FALSE,
    confirm_time            TIMESTAMPTZ,
    delmark                 SMALLINT     NOT NULL DEFAULT 0,
    create_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_inspection_request_register_id ON inspection_request(register_id);
CREATE INDEX IF NOT EXISTS ix_inspection_request_status ON inspection_request(status);

CREATE TABLE IF NOT EXISTS disposal_request (
    id                      BIGSERIAL PRIMARY KEY,
    register_id             BIGINT       NOT NULL REFERENCES register(id),
    patient_id              BIGINT       NOT NULL REFERENCES patient(id),
    medical_technology_id   BIGINT       NOT NULL REFERENCES medical_technology(id),
    doctor_id               BIGINT       NOT NULL REFERENCES employee(id),
    item_price              NUMERIC(10,2) NOT NULL,
    purpose                 VARCHAR(256),
    body_part               VARCHAR(64),
    remark                  VARCHAR(256),
    status                  SMALLINT     NOT NULL DEFAULT 10,
    order_time              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    executor_id             BIGINT REFERENCES employee(id),
    execute_time            TIMESTAMPTZ,
    result_input_id         BIGINT REFERENCES employee(id),
    result_time             TIMESTAMPTZ,
    result_text             TEXT,
    result_attachment       VARCHAR(512),
    from_ai                 BOOLEAN      NOT NULL DEFAULT FALSE,
    confirm_time            TIMESTAMPTZ,
    delmark                 SMALLINT     NOT NULL DEFAULT 0,
    create_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_disposal_request_register_id ON disposal_request(register_id);
CREATE INDEX IF NOT EXISTS ix_disposal_request_status ON disposal_request(status);

-- =============================================================================
-- E. 处方
-- =============================================================================

CREATE TABLE IF NOT EXISTS ai_prescription_draft (
    id                      BIGSERIAL PRIMARY KEY,
    register_id             BIGINT       NOT NULL REFERENCES register(id),
    doctor_id               BIGINT       NOT NULL REFERENCES employee(id),
    draft_content           JSONB        NOT NULL,
    doctor_edited_content   JSONB,
    status                  SMALLINT     NOT NULL DEFAULT 0,
    submit_time             TIMESTAMPTZ,
    create_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 业务 ID 即 id；开立/发药时间见 create_time / update_time
CREATE TABLE IF NOT EXISTS prescription (
    id              BIGSERIAL PRIMARY KEY,
    register_id     BIGINT       NOT NULL REFERENCES register(id),
    patient_id      BIGINT       NOT NULL REFERENCES patient(id),
    doctor_id       BIGINT       NOT NULL REFERENCES employee(id),
    total_amount    NUMERIC(10,2) NOT NULL DEFAULT 0,
    status          SMALLINT     NOT NULL DEFAULT 10,
    pharmacist_id   BIGINT REFERENCES employee(id),
    ai_draft_id     BIGINT REFERENCES ai_prescription_draft(id),
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_prescription_status ON prescription(status);
CREATE INDEX IF NOT EXISTS ix_prescription_register_id ON prescription(register_id);
CREATE INDEX IF NOT EXISTS ix_prescription_patient_id ON prescription(patient_id);

CREATE TABLE IF NOT EXISTS prescription_item (
    id              BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT       NOT NULL REFERENCES prescription(id),
    drug_id         BIGINT       NOT NULL REFERENCES drug_info(id),
    drug_code       VARCHAR(32)  NOT NULL,
    drug_name       VARCHAR(128) NOT NULL,
    drug_format     VARCHAR(255),
    drug_dosage     VARCHAR(64),
    drug_type       VARCHAR(64),
    unit_price      NUMERIC(10,2) NOT NULL,
    quantity        NUMERIC(10,2) NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    usage_method    VARCHAR(64),
    dosage          VARCHAR(64),
    frequency       VARCHAR(64),
    days            INTEGER,
    entrust         VARCHAR(256),
    sort_no         INTEGER      DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_prescription_item_prescription_id ON prescription_item(prescription_id);

-- =============================================================================
-- F. 收费支付（按单付 · 无余额；业务单号即各表 id）
-- =============================================================================

CREATE TABLE IF NOT EXISTS bill (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT       NOT NULL REFERENCES patient(id),
    register_id     BIGINT REFERENCES register(id),
    biz_type        VARCHAR(16)  NOT NULL,
    biz_id          BIGINT       NOT NULL,
    bill_title      VARCHAR(128) NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    status          SMALLINT     NOT NULL DEFAULT 0,
    paid_time       TIMESTAMPTZ,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (biz_type, biz_id)
);
CREATE INDEX IF NOT EXISTS ix_bill_patient_id ON bill(patient_id);
CREATE INDEX IF NOT EXISTS ix_bill_register_id ON bill(register_id);
CREATE INDEX IF NOT EXISTS ix_bill_biz ON bill(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS ix_bill_status ON bill(status);

CREATE TABLE IF NOT EXISTS payment_record (
    id                  BIGSERIAL PRIMARY KEY,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    total_amount        NUMERIC(10,2) NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    status              SMALLINT     NOT NULL DEFAULT 0,
    third_party_trade_no VARCHAR(64),
    operator_id         BIGINT REFERENCES employee(id),
    pay_time            TIMESTAMPTZ,
    remark              VARCHAR(256),
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_payment_record_patient_id ON payment_record(patient_id);
CREATE INDEX IF NOT EXISTS ix_payment_record_status ON payment_record(status);
CREATE INDEX IF NOT EXISTS ix_payment_record_third_party ON payment_record(third_party_trade_no);

CREATE TABLE IF NOT EXISTS payment_bill (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      BIGINT       NOT NULL REFERENCES payment_record(id),
    bill_id         BIGINT       NOT NULL REFERENCES bill(id),
    amount          NUMERIC(10,2) NOT NULL,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (payment_id, bill_id)
);
CREATE INDEX IF NOT EXISTS ix_payment_bill_payment_id ON payment_bill(payment_id);
CREATE INDEX IF NOT EXISTS ix_payment_bill_bill_id ON payment_bill(bill_id);

CREATE TABLE IF NOT EXISTS refund_record (
    id                  BIGSERIAL PRIMARY KEY,
    payment_id          BIGINT       NOT NULL REFERENCES payment_record(id),
    bill_id             BIGINT REFERENCES bill(id),
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    refund_amount       NUMERIC(10,2) NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    status              SMALLINT     NOT NULL DEFAULT 0,
    third_party_refund_no VARCHAR(64),
    operator_id         BIGINT REFERENCES employee(id),
    refund_time         TIMESTAMPTZ,
    reason              VARCHAR(256),
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_refund_record_payment_id ON refund_record(payment_id);
CREATE INDEX IF NOT EXISTS ix_refund_record_patient_id ON refund_record(patient_id);

-- =============================================================================
-- G. 影像与 AI 会话
-- =============================================================================

CREATE TABLE IF NOT EXISTS imaging_study (
    id                  BIGSERIAL PRIMARY KEY,
    study_no            VARCHAR(32)  NOT NULL UNIQUE,
    check_request_id    BIGINT REFERENCES check_request(id),
    register_id         BIGINT       NOT NULL REFERENCES register(id),
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    modality            VARCHAR(16),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    source_bucket       VARCHAR(64),
    source_object_key   VARCHAR(512),
    result_bucket       VARCHAR(64),
    result_object_key   VARCHAR(512),
    report_json         JSONB,
    error_message       VARCHAR(512),
    submit_time         TIMESTAMPTZ,
    complete_time       TIMESTAMPTZ,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_imaging_study_register_id ON imaging_study(register_id);
CREATE INDEX IF NOT EXISTS ix_imaging_study_status ON imaging_study(status);

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id              BIGSERIAL PRIMARY KEY,
    session_no      VARCHAR(32)  NOT NULL UNIQUE,
    scene           VARCHAR(32)  NOT NULL,
    patient_id      BIGINT REFERENCES patient(id),
    register_id     BIGINT REFERENCES register(id),
    doctor_id       BIGINT REFERENCES employee(id),
    messages        JSONB,
    delmark         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_ai_chat_session_scene ON ai_chat_session(scene);

-- end of schema.sql
