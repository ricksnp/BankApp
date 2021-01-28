package com.bank.driver;

public class Account { // Account fields
	public String accountname;
	public double balance;
	public boolean confirmed;
	public int id;

	public Account(int i, String a, double b, boolean conf) { // Account constructor
		this.id = i;
		this.accountname = a;
		this.balance = b;
		this.confirmed = conf;
	}

}

