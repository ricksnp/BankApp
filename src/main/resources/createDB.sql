-- bankv3.users definition

-- Drop table

-- DROP TABLE bankv3.users;

CREATE TABLE bankv3.users (
	id int4 NOT NULL GENERATED ALWAYS AS IDENTITY,
	username varchar NULL,
	pass varchar NOT NULL,
	usertype varchar NULL,
	confirmed bool NULL,
	CONSTRAINT users_pkey PRIMARY KEY (id),
	CONSTRAINT users_username_key UNIQUE (username)
);


-- bankv3.loans definition

-- Drop table

-- DROP TABLE bankv3.loans;

CREATE TABLE bankv3.loans (
	id int4 NULL,
	loanname varchar NULL,
	balance money NULL,
	confirmed bool NULL,
	CONSTRAINT loans_id_fkey FOREIGN KEY (id) REFERENCES bankv3.users(id)
);


-- bankv3.accounts definition

-- Drop table

-- DROP TABLE bankv3.accounts;

CREATE TABLE bankv3.accounts (
	id int4 NULL,
	accountname varchar NULL,
	balance money NULL,
	confirmed bool NULL,
	CONSTRAINT accounts_id_fkey FOREIGN KEY (id) REFERENCES bankv3.users(id)
);


-- bankv3.pendingtransfer definition

-- Drop table

-- DROP TABLE bankv3.pendingtransfer;

CREATE TABLE bankv3.pendingtransfer (
	senderid int4 NULL,
	recepientid int4 NULL,
	amount money NULL,
	completed bool NULL,
	sendingaccountname varchar NULL,
	CONSTRAINT pendingtransfer_recepientid_fkey FOREIGN KEY (recepientid) REFERENCES bankv3.users(id),
	CONSTRAINT pendingtransfer_senderid_fkey FOREIGN KEY (senderid) REFERENCES bankv3.users(id)
);


-- bankv3.log definition

-- Drop table

-- DROP TABLE bankv3.log;

CREATE TABLE bankv3.log (
	dt timestamp NULL DEFAULT CURRENT_TIMESTAMP,
	username varchar NULL,
	message varchar NULL
);




		
	
create function loansOutstanding()
returns varchar
language plpgsql
as
$$
declare
   amount varchar;
begin
   select sum(balance) into amount from bankv3.loans;
   return amount;
end;
$$;