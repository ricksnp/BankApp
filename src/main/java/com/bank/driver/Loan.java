package com.bank.driver;

public class Loan { // Loan fields
	double balance;
	boolean confirmed;
	int id;
	String loanname;

	public Loan(int i, String l, double b, boolean c) { // Loan constructor
		this.id = i;
		this.loanname = l;
		this.balance = b;
		this.confirmed = c;
	}
}
