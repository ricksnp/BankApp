package com.bank.driver;

public class PendingTransfer { // PendingTransfer fields
	double amount;
	boolean completed;
	int recepientid;
	int senderid;
	String sendingAccountName;

	public PendingTransfer(int s, int r, double a, boolean c, String S) { // PendingTransfer constructor
		this.senderid = s;
		this.recepientid = r;
		this.amount = a;
		this.completed = c;
		this.sendingAccountName = S;
	}

}