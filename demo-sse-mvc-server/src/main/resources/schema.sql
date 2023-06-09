create sequence if not exists SEQ_SSE_EVENT_ID start with 1 increment by 1 no cache;

create table if not exists SSE_EVENT (
    ID bigint default next value for SEQ_SSE_EVENT_ID not null primary key,
    NAME varchar(255) not null,
    REF_ID varchar(255),
    DATA clob not null,
    CREATED_DTTM timestamp not null
)