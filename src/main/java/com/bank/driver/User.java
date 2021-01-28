package com.bank.driver;

public class User { // User fields
	boolean confirmed;
	int id;
	String name;
	String password;
	String typeOfUser;

	public User(int i, String n, String p, String t, boolean c) { // User constructor
		this.id = i;
		this.name = n;
		this.password = p;
		this.typeOfUser = t;
		this.confirmed = c;
	}
}
