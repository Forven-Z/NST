-- 旧库升级：排班请假（2026-06-12）
-- 用法: psql -U postgres -d hospital -f docs/sql/patch-scheduling-leave.sql

CREATE TABLE IF NOT EXISTS scheduling_leave_request (
    id                      BIGSERIAL PRIMARY KEY,
    scheduling_id           BIGINT       NOT NULL REFERENCES scheduling(id),
    employee_id             BIGINT       NOT NULL REFERENCES employee(id),
    reason                  VARCHAR(256) NOT NULL,
    status                  SMALLINT     NOT NULL DEFAULT 0,
    approve_admin_id        BIGINT,
    approve_time            TIMESTAMPTZ,
    reject_remark           VARCHAR(256),
    substitute_employee_id  BIGINT REFERENCES employee(id),
    substitute_time         TIMESTAMPTZ,
    create_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_leave_request_status ON scheduling_leave_request(status, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_leave_request_active
    ON scheduling_leave_request(scheduling_id)
    WHERE status IN (0, 1);
