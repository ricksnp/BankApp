package com.bank.driver;

import java.util.*;
import org.junit.*;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.Map;
import org.apache.log4j.Logger;  

public class Bank {
	
	public Bank() {
		String url = System.getenv("url");
		String username = System.getenv("username");
		String password = System.getenv("password");
		try {
			conn = DriverManager.getConnection(url, username, password);
		} catch (Exception e) {
			e.printStackTrace();
			err(e.getClass().getName() + ": " + e.getMessage());
			System.exit(0);
		}
	}
	
	public static void main(String[] args) {
		Bank b = new Bank();
		//b.initializeDB();
		say("\n\tWelcome to Non-Cents Bank & Trust\n\n");
		say(" _._._                       _._._\r\n"
				+ "        _|   |_                     _|   |_\r\n"
				+ "        | ... |_._._._._._._._._._._| ... |\r\n"
				+ "        | ||| |  o NATIONAL BANK o  | ||| |\r\n"
				+ "        | \"\"\" |  \"\"\"    \"\"\"    \"\"\"  | \"\"\" |\r\n"
				+ "   ())  |[-|-]| [-|-]  [-|-]  [-|-] |[-|-]|  ())\r\n"
				+ "  (())) |     |---------------------|     | (()))\r\n"
				+ " (())())| \"\"\" |  \"\"\"    \"\"\"    \"\"\"  | \"\"\" |(())())\r\n"
				+ " (()))()|[-|-]|  :::   .-\"-.   :::  |[-|-]|(()))()\r\n"
				+ " ()))(()|     | |~|~|  |_|_|  |~|~| |     |()))(()\r\n"
				+ "    ||  |_____|_|_|_|__|_|_|__|_|_|_|_____|  ||\r\n"
				+ " ~ ~^^ @@@@@@@@@@@@@@/=======\\@@@@@@@@@@@@@@ ^^~ ~\r\n"
				+ "      ^~^~                                ~^~^\n");
		
		b.showStartingMenu();
	}
	
//-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

	/**
	 * Global variables + NOTE:  Database Creating file in src/main/resources
	 */

	Connection conn = null;
	public Account currentAccount;
	public Loan currentLoan;
	public PendingTransfer currentPendingTransfer;
	public User currentUser;
	final static double MAX_DEPOSIT = 5000.00;                // Customer cannot deposit more than $5000.00 at one time
	final static double MAX_LOAN = 100000.00;                 // Customer cannot apply for a loan greater than $100,000.00
	final static double MIN_PAYMENT = 50;                     // On any loan, the minimum a user can pay is $50.00
	final static double MIN_LOAN = 1000;                      // Our bank only provides loans with a minimum amount of $1000.00
	final static Logger logger = Logger.getLogger("Bank");
	List<String> sqlStatements = new ArrayList<String>();
	public boolean testing = false;
	

	/**
	 * Prompt the customer which account they would like to deposit into and get the
	 * amount. Update the account balance in the datbase.
	 */
	public void deposit() {
		selectAccount("Would you like to deposit into this account?");
		if (currentAccount != null) {
			double depAmount = getNumber("\nHow much would you like to deposit? ", MAX_DEPOSIT);
			currentAccount.balance += depAmount;
			say("\nDeposit complete! Your new balance is: $" + formatBalance(currentAccount.balance) + "\n");
			update("accounts", "balance", "" + currentAccount.balance,
					"id = '" + currentUser.id + "' AND accountname = '" + currentAccount.accountname + "'");

			logMessage("TRANS: " + currentUser.name + " deposited " + depAmount + " into his account: "
					+ currentAccount.accountname);
			currentAccount = null; // set the global variable back to null
		}
	}

	/**
	 * Prompt the user for their username and password and then compare the
	 * credentials to the ones stored in the datbase. If they do not match, print
	 * the respective error message.
	 */
	public void login() {
		currentUser = null; // logout the previous user
		String username = getUsernameOrPassword("Username: ");
		String password = getUsernameOrPassword("Password: ");
		User u = getAllUserDetailsByName(username);

		if (u != null) {
			if (!u.confirmed) {
				err("\nYou must be approved in order to login!");
				logMessage(username + " failed to login due to not being approved yet!");
				showStartingMenu();
			} else if (u.password.equals(password)) {
				say("\nLogin Successful!");
				currentUser = u;
				logMessage(currentUser.name + " logged in.");
			} else {
				err("Incorrect Password!\n");
				showStartingMenu();
				logMessage(currentUser.name + " failed to login due to an incorrect password attempt");
			}
		} else {
			err("\nNo such user: " + username);
			showStartingMenu();
		}

	}

	/**
	 * Alert the user they are being logged out, and then set the global variable
	 * currentUser to null.
	 */
	public void logout() {
		say("\nNow logging out!  Goodbye, " + currentUser.name);
		logMessage(currentUser.name + " logged out!");
		currentUser = null;
	}

	/**
	 * Prompt the customer to select which account they would like to view the
	 * balance of. Once selected, display that balance.
	 */
	public void viewBalance() {
		selectAccount("Would you like to check the balance of this account?");
		if (currentAccount != null) {
			say("\nBalance: $" + formatBalance(currentAccount.balance));
			logMessage(currentUser.name + " viewed his balance");
			currentAccount = null;
		}
	}

	/**
	 * Prompt the Manager to enter the username and password of the employee they'd
	 * like to add to the system, and then add them, alerting the Manager that they
	 * have been added.
	 */
	public void hireEmployee() {
		if (currentUser.typeOfUser.equals("Manager")) {
			String username = getUsernameOrPassword("\nEnter their username: ");
			String password = getUsernameOrPassword("Enter their password: ");
			String type = "Employee";
			insert("users", "username, pass, usertype, confirmed",
					"'" + username + "' ,  '" + password + "', '" + type + "'," + " '" + true + "'");
			say("\nSuccessfully added employee " + username + " to the system!");
		}

		else {
			err("\nNo permission!");
		}
	}

	/**
	 * Fetch all bank accounts that are pending approval. If this results in 0,
	 * alert the employee that there are no accounts pending approval. Otherwise,
	 * sift through the accounts, prompting the employee to either approve or reject
	 * the bank accounts. The database is updated to reflect these decisions.
	 */
	public void approveBankAccount() {
		if (!currentUser.typeOfUser.equals("Customer")) {
			List<Account> accounts = getBankAccountsNeedingApproval();

			if (accounts.isEmpty()) {
				System.out.println("\nThere are no accounts to approve!\n");
				printEmployeeMenu();
			}

			for (Account a : accounts) {
				if (!a.confirmed) {
					User u = getAllUserDetailsById(a.id);
					int choice = getNumberInRange("Would you like to approve the account for " + u.name
							+ " named " + a.accountname + " with a starting balance of $" + a.balance + "?\n"
							+ "Enter 1 for yes, or 2 for no: ", 2);
					if (choice == 1) {
						update("accounts", "confirmed", "true",
								"id = '" + a.id + "' AND accountname = '" + a.accountname + "'");
						say("\nAccount " + a.accountname + " accepted!");
						logMessage("Employee " + currentUser.name + "accepted the account  " + a.accountname
								+ " for " + u.name + " with an initial balance of " + a.balance);
					} else if (choice == 2) {
						delete("accounts",
								" id = " + a.id + " AND accountname = '" + a.accountname + "' AND NOT confirmed");
						say("\nAccount " + a.accountname + " not accepted!");
						logMessage("Employee " + currentUser.name + " rejected the account  " + a.accountname
								+ " for " + u.name + " with an initial balance of " + a.balance);
					}
				}
			}
		} else {
			err("\nNo permission!");
		}
	}

	/**
	 * Fetch all loans needing approval, and return it here. Prompt the employee
	 * whether or not they'd like to approve or reject each loan. The database is
	 * then updated to reflect these decisions.
	 */
	public void approveLoan() {
		if (!currentUser.typeOfUser.equals("Customer")) {
			List<Loan> loans = getUnapprovedLoans();
			for (Loan l : loans) {
				if (!l.confirmed) {
					int choice = getNumberInRange(
							"\nWould you like to confirm the loan for " + l.id + " for the reason " + l.loanname
									+ " for the amount of " + l.balance + "?\n" + "Enter 1 for yes, or 2 for no: ",
							2);

					if (choice == 1) {
						update("loans", "confirmed", "true",
								"id = '" + l.id + "' AND loanname = '" + l.loanname + "'");
						say("\nLoan " + l.loanname + " accepted!");
						logMessage("Employee " + currentUser.name + " accepted loan for " + l.id
								+ "for the reason " + l.loanname + "for the amount of " + l.balance);
					} else if (choice == 2) {
						delete("loans", " id = " + l.id + " AND loanname = '" + l.loanname + "' AND NOT confirmed");
						err("\nLoan " + l.loanname + " not accepted!");
						logMessage("Employee " + currentUser.name + " rejected loan for " + l.id
								+ "for the reason " + l.loanname + " for the amount of " + l.balance);
					} else {
						approveLoan();
					}
				}
			}
		} else {
			err("\nNo permission!");
		}
	}

	/**
	 * Retrieve all users in the system and return them to this method. Sift through
	 * each employee, and if they are not confirmed, prompt the Employee to confirm
	 * or reject those users.
	 */
	public void approveNewUser() {
		if (!currentUser.typeOfUser.equals("Customer")) {
			Map<String, User> allUsers = getAllUserDetails();
			for (String us : allUsers.keySet()) {
				User u = allUsers.get(us);
				if (!u.confirmed) {
					int choice = getNumberInRange(
							"\nWould you like to confirm the user " + us + "?\n\n" + "Enter 1 for yes, or 2 for no: ",
							2);
					if (choice == 1) {
						update("users", "confirmed", "true", "username = '" + u.name + "'");
						say("\nUser " + us + " accepted!");
						logMessage(
								"Employee " + currentUser.name + "accepted user " + us + " into the system!");
					}
					if (choice == 2) {
						say("\nUser " + us + " not accepted!");
						delete("users", "id = " + u.id + "");
						logMessage(
								"Employee " + currentUser.name + "rejected user " + us + " from joining the system!");
					}
				}
			}
		} else {
			err("\nNo permission!");
		}
	}

	/**
	 * Display all accounts tied to the current customer. Prompt them whether or not
	 * they would like to choose the current account. This method is relevant to any
	 * deposit, withdrawal, or transfer etc.
	 * 
	 * @param prompt would they like to choose this account
	 */
	public void selectAccount(String prompt) {
		List<Account> allAccounts = getAccountDetailsById(currentUser.id);
		if (allAccounts.isEmpty()) {
			err("\nNo accounts available!");
			return;
		}
		for (Account a : allAccounts) {
			say("\nAccount: " + a.accountname + "\n");
			int choice = getNumberInRange(prompt + " Enter 1 for yes, or 2 for no: ", 2);
			if (choice == 1) {
				currentAccount = a;
				return;
			}
		}
		err("\nNo account chosen!\n");
		return;
	}

	/**
	 * Prompt the Manager which employee they would like to delete, and then
	 * retrieve their username. Delete the employee associated with that username.
	 */
	public void fireEmployee() {
		if (currentUser.typeOfUser.equals("Manager")) {
			String employeeToDelete = getUsernameOrPassword("Who would you like to terminate?  Enter their username: ");
			User u = getAllUserDetailsByName(employeeToDelete);
			if (u != null) {
				delete("users", "id = " + u.id + ";");
				say("\nEmployee " + u.name + " removed from the system!");
			} else {
				err("\nUser " + employeeToDelete + " could not be found!");
			}
		} else {
			err("\nNo Permission!");
		}
	}

	/**
	 * Log all actions taken within the app to the database to be stored.
	 * Simultaneously, logs are pushed locally to a file in order to be viewed.
	 * 
	 * @param msg the message to be logged
	 */
	public void logMessage(String msg) {
		try {
			String name = currentUser == null ? "" : currentUser.name;
			String sql = "INSERT INTO bankv3.log (username, message) values (?,?)";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, name);
			pstmt.setString(2, msg);
			pstmt.executeUpdate();
			logger.info(msg);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Prompt the customer for an account their account, an amount and a recepient.
	 * Write this information into the PendingTransfers table. The actual transfer
	 * will be completed when the recepient logs on and accepts the transfer.
	 */
	public void transferBetweenDifferentUsers() { 
		selectAccount("Would you like to transfer from this account?");
		if (currentAccount != null) {

			if (currentAccount.balance == 0) {
				err("\nSorry, you cannot make a transfer from this account as you have insufficient funds.\n");
				return;
			}

			double bal = currentAccount.balance;
			say("\nBalance: $" + formatBalance(bal));

			double transferAmount = getNumber("\nHow much would you like to transfer? ", bal);
			String transferTo = getUsernameOrPassword("\nWho would you like to transfer to? ");

			User u = getAllUserDetailsByName(transferTo);
			if (u != null) {
				Account debitedAccount = currentAccount;
				User debitedUser = currentUser;
				insert("pendingtransfer", "senderid, recepientid, amount, completed, sendingAccountName",
						"" + currentAccount.id + ", " + u.id + ", " + transferAmount + ", '" + false + "', '"
								+ debitedAccount.accountname + "'");

				say("\nYour transfer will be complete once " + u.name + " accepts the transfer!\n");
				logMessage("TRANS: " + debitedUser.name + " started a transfer of $" + formatBalance(transferAmount) + " to "
						+ currentUser.name);
				currentAccount = null;
			}
		}
	}

	/**
	 * Prompt the current customer which account of theirs they would like to
	 * transfer from, and the amount. Then, have them choose an account of theirs
	 * that they would like to transfer to. The database is then updated to reflect
	 * the transfer.
	 */
	public void transferBetweenSameUser() { // between accounts owned by the same user

		selectAccount("Would you like to transfer from this account?");
		if (currentAccount != null) {

			if (currentAccount.balance == 0) {
				err("\nSorry, you cannot make a transfer from this account as you have insufficient funds.\n");
				return;
			}
			double bal = currentAccount.balance;
			say("\nBalance: $" + formatBalance(bal) + "\n");
			double transferAmount = getNumber("How much would you like to transfer? ", bal);
			Account debitedAccount = currentAccount;
			selectAccount("Would you like to deposit into this account?"); // choose target account
			if (currentAccount != null) {

				currentAccount.balance += transferAmount;
				debitedAccount.balance -= transferAmount;
				say("\nYou transferred $" + formatBalance(transferAmount) + " from " + debitedAccount.accountname + "! [Balance is now: $"
						+ formatBalance(debitedAccount.balance) + "]" + " to " + currentAccount.accountname + "! [Balance is now: "
						+ currentAccount.balance + "]");
				logMessage("TRANS: " + currentUser.name + " transfered " + transferAmount + " to "
						+ currentAccount.accountname + " from " + debitedAccount.accountname);

				update("accounts", "balance", "" + debitedAccount.balance,
						"id = " + currentUser.id + " AND accountname = '" + debitedAccount.accountname + "'");
				update("accounts", "balance", "" + currentAccount.balance,
						"id = " + currentUser.id + " AND accountname = '" + currentAccount.accountname + "'");

			}
			currentAccount = null;
		}
	}

	/**
	 * Display all pending money transfers a customer has coming to them. For each
	 * pending transfer, prompt the customer to accept or reject the incoming
	 * transfers, and update the database accordingly based on their decision.
	 */
	public void viewPendingTransfers() {
		List<PendingTransfer> list = getPendingTransfers();

		if (list.isEmpty()) {
			err("\nThere are no current pending transfers!\n");
		}

		for (PendingTransfer transfer : list) {
			String from = getUserName(transfer.senderid);
			say("\nMoney transfer from: " + from + " for the amount of $" + formatBalance(transfer.amount) + "\n");
			int choice = getNumberInRange("Would you like to accept this transfer? Enter 1 for yes, or 2 for no: ", 2);
			if (choice == 1) {
				selectAccount("Would you like to deposit into this account?");
				if (currentAccount != null) {
					currentAccount.balance += transfer.amount;
					update("accounts", "balance", "" + currentAccount.balance,
							"id = " + currentUser.id + " AND accountname = '" + currentAccount.accountname + "'");
					User creditUser = currentUser;
					Account creditAccount = currentAccount;
					String debitedUserName = getUserName(transfer.senderid);
					User debitedUser = getAllUserDetailsByName(debitedUserName);
					Account debitedAccount = getAccountDetailsByNameAndId(transfer.senderid, transfer.sendingAccountName);
					if (debitedAccount != null && debitedUser != null) {
						debitedAccount.balance -= transfer.amount;
						update("accounts", "balance", "" + debitedAccount.balance,
								"id = " + debitedUser.id + " AND accountname = '" + debitedAccount.accountname + "'");

						delete("pendingtransfer", "recepientid = " + currentUser.id + "");
						logMessage(debitedUser.name + " transfered " + transfer.amount + " to "
								+ creditUser.name + " from account " + debitedAccount.accountname + " to account "
								+ creditAccount.accountname);

						say("\nYou transferred $" + formatBalance(transfer.amount) + " from " + debitedUser.name + " to "
								+ creditAccount.accountname + "! [Balance is now: $" + formatBalance(creditAccount.balance) + "]\n");
					} else {
						err("\nCannot complete transfer! Sending account has disappeared!");
					}
				}
			} else if (choice == 2) {
				err("\nRejected transfer!\n");
				delete("pendingtransfer", "recepientid = " + currentUser.id + "");
			}
		}
	}

	/**
	 * Prompt the customer which account of theirs they would like to withdraw from
	 * and an amount. Deduct that amount from the account's balance and update the
	 * database accordingly.
	 */
	public void withdraw() {
		selectAccount("Would you like to withdraw from this account?");
		if (currentAccount != null) {
			if (currentAccount.balance == 0) {
				err("\nSorry, you cannot make a withdrawal from this account as you have insufficient funds.\n");
				return;
			}
			double bal = currentAccount.balance;
			say("\nBalance: $" + formatBalance(bal) + "\n");
			double withAmount = getNumber("How much would you like to withdraw? ", bal);
			currentAccount.balance -= withAmount; // update the current users balance
			say("\nYour new balance is: $" + formatBalance(currentAccount.balance) + "\n");
			update("accounts", "balance", "" + currentAccount.balance,
					"id = '" + currentUser.id + "' AND accountname = '" + currentAccount.accountname + "'");
			logMessage("TRANS: " + currentUser.name + " made a withdrawal of " + withAmount
					+ " from the account " + currentAccount.accountname);
			currentAccount = null;
		}
	}

	/**
	 * Display all loans tied to the current customer in the system. Prompt the user
	 * whether or not they would like to make payments towards that loan. If yes,
	 * take the amount they pay, subtract it from the balance, and update the
	 * balance in the database. Then, alert them how many more monthly payments they
	 * have left.
	 */
	public void makeLoanPayment() {
		List<Loan> loans = viewLoans();

		for (Loan l : loans) {
			final int choice = getNumberInRange("\nWould you like to make a payment towards " + l.loanname
					+ " with an outstanding balance of " + l.balance + "? Enter 1 for yes, or 2 for no: ", 2);
			if (choice == 1) {
				double amountToPay = getNumber(
						"How much would you like to pay (Minimum payment: $" + MIN_PAYMENT + ")? ", l.balance);
				l.balance -= amountToPay;
				int paymentsLeft = (int) (l.balance / MIN_PAYMENT);
				update("loans", "balance", "" + l.balance,
						"id = '" + l.id + "' AND loanname = '" + l.loanname + "'");
				say("\nPayment on loan " + l.loanname + " of $" + formatBalance(amountToPay) + " accepted. Remaining balance is $"
						+ formatBalance(l.balance) + ".\n");

				if (l.balance == 0) {
					say("You have no remaining payments towards this loan. Thank you for your business!");
					delete("loans", "id = " + l.id + " AND loanname = '" + l.loanname + "';");
				}

				else {
					say("You have " + paymentsLeft
							+ " payment(s) left if you only pay the minimum per month.  You can pay more at any time!\n");
					logMessage("TRANS:  User " + currentUser.name + " made a payment of " + amountToPay + " towards "
							+ l.loanname + " remaining balance is: " + formatBalance(l.balance));
				}

			} else if (choice == 2) {
				say("\nWe sincerely hope that you will make more payments of " + MIN_PAYMENT + " soon!");
				logMessage("Customer " + currentUser.name + " did not make a payment against loan "
						+ l.loanname + " with balance $" + formatBalance(l.balance));
			}
		}
	}

	/**
	 * Prompt the employee to enter a customer name to search account details for.
	 * All accounts tied to that user are returned to the employee, with their
	 * respective balances.
	 */
	public void viewCustomerAccounts() {
		if (!currentUser.typeOfUser.equals("Customer")) {
			String customerName = getUsernameOrPassword("\nEnter customer's username: ");
			final User customer = getAllUserDetailsByName(customerName);
			System.out.println();
			if (customer != null) {
				List<Account> accounts = getAccountDetailsById(customer.id);
				for (Account a : accounts) {
					say("Account Name: " + a.accountname + "\nAccount Balance: $" + formatBalance(a.balance) + "\n");
				}

			} else {
				err("NO such customer: " + customerName);
			}
			logMessage("Employee " + currentUser.name + " viewed accounts of: " + customerName);
		} else {
			err("\nNo permission!");
		}
	}

	/**
	 * Count the number of records in a certain table after certain tests are ran.
	 * This is to make sure we are getting expected results after testing certain
	 * functionality in the program.
	 * 
	 * @param table        the table to select from
	 * @param where        clause
	 * @param expectedRows
	 */
	public void countRecordsInTable(String table, String where, int expectedRows) {
		try {
			Statement stmt = conn.createStatement();
			String query = "SELECT  count(*)  AS Result FROM bankv3. " + table
					+ (where.equals("") ? "" : (" where  " + where));
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				int count = rs.getInt("Result");
				if (count != expectedRows) {
					say("Select statement " + query);
					say("*************Expected " + expectedRows + " but got " + count + " rows");
					Assert.fail("should not reach here");
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Instead of have a delete method for each table in the DB, make one universal
	 * delete method and allow it to be called by filling in the appropriate table
	 * name and the where clause.
	 * 
	 * @param tab   the table
	 * @param where the where clause
	 */
	public void delete(String tab, String where) {
		try {
			String sql = "delete from bankv3." + tab + " where " + where + ";";
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Fetch all accounts tied to a certian customer's id.
	 * 
	 * @param id the id to search
	 * @return all accounts related to this user, as a list
	 */
	public List<Account> getAccountDetailsById(int id) {
		List<Account> accounts = new ArrayList<Account>();
		try {
			String sql = "SELECT * FROM bankv3.accounts WHERE id = ? AND confirmed";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, id);
			pstmt.executeQuery();
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				String accountname = rs.getString("accountname");
				String balance = rs.getString("balance");
				boolean confirmed = rs.getBoolean("confirmed");

				Account a = new Account(id, accountname, convertMoneyToDouble(balance), confirmed);
				accounts.add(a);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return accounts;
	}

	/**
	 * Fetch a specific bank account tied to a certain user, using their id and the
	 * name of the account
	 * 
	 * @param id
	 * @param nameOfAccount
	 * @return the specific account
	 */
	public Account getAccountDetailsByNameAndId(int id, String nameOfAccount) {

		try {
			String query = "SELECT * FROM bankv3.accounts where accountname = ? AND id = ? ";
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setString(1, nameOfAccount);
			pstmt.setInt(2, id);
			// pstmt.executeQuery();
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				String balance = rs.getString("balance");
				boolean conf = rs.getBoolean("confirmed");
				Account a = new Account(currentUser.id, nameOfAccount, convertMoneyToDouble(balance), conf);
				rs.close();
				return a;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Retrieve all bank accounts that need approval
	 * 
	 * @return a list of unapproved bank accounts
	 */
	public List<Account> getBankAccountsNeedingApproval() {
		List<Account> accounts = new ArrayList<Account>();
		try {
			String sql = "SELECT * FROM bankv3.accounts WHERE NOT confirmed";
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				int userId = rs.getInt("id");
				String accountname = rs.getString("accountname");
				String initial = rs.getString("balance");
				Account a = new Account(userId, accountname, convertMoneyToDouble(initial), false);
				accounts.add(a);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return accounts;
	}

	/**
	 * Retrieve all approved loans tied to a user, using their id.
	 * 
	 * @return a list of approved loans for a specific user
	 */
	public List<Loan> viewLoans() {
		List<Loan> loans = new ArrayList<Loan>();
		try {
			String sql = "SELECT * FROM bankv3.loans WHERE confirmed AND id =  ?";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, currentUser.id);
			pstmt.executeQuery();
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				String loanname = rs.getString("loanname");
				String loanAmount = rs.getString("balance");
				loans.add(new Loan(currentUser.id, loanname, convertMoneyToDouble(loanAmount), true));
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return loans;
	}

	/**
	 * Retrieve all log entries stored in the database, sorted by date. This will be
	 * used by any manager.
	 */
	public void showAllLogs() {
		if (currentUser.typeOfUser.equals("Manager")) {
			try {
				Statement stmt = conn.createStatement();
				String query = "SELECT * FROM bankv3.log order by dt;";
				ResultSet rs = stmt.executeQuery(query);

				while (rs.next()) {
					String date = rs.getString("dt");
					String name = rs.getString("username");
					String msg = rs.getString("message");
					say(date + " " + name + " " + msg);
				}
				rs.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		else {
			err("\nNo Permission!");
		}
	}

	/**
	 * Retrieve only transaction logs, for any employee.
	 */
	public void showAllTransactionLogs() {
		if (!currentUser.typeOfUser.equals("Customer")) {
			try {
				Statement stmt = conn.createStatement();
				String query = "SELECT * FROM bankv3.log where message like'TRANS%' order by dt;";
				ResultSet rs = stmt.executeQuery(query);

				while (rs.next()) {
					String date = rs.getString("dt");
					String msg = rs.getString("message");

					say(date + " " + msg);
				}
				rs.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			err("\nNo permission!");
		}
	}

	/**
	 * Retrieve all loans in the loan table that need approval
	 * 
	 * @return a list of unapproved loans
	 */
	public List<Loan> getUnapprovedLoans() {

		List<Loan> loans = new ArrayList<Loan>();
		try {
			String sql = "SELECT * FROM bankv3.loans WHERE NOT confirmed";
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				int userId = rs.getInt("id");
				String loanname = rs.getString("loanname");
				String loanAmount = rs.getString("balance");
				String s = loanAmount.replaceAll("[$,]", "");
				double d = Double.parseDouble(s);
				Loan a = new Loan(userId, loanname, d, false);
				loans.add(a);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return loans;
	}

	/**
	 * Retrieve all pending transfers for the current user
	 * 
	 * @return a list of pending transfers
	 */
	public List<PendingTransfer> getPendingTransfers() {
		List<PendingTransfer> list = new ArrayList<PendingTransfer>();
		try {
			String query = "SELECT * FROM bankv3.pendingtransfer where recepientid = ?";
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setInt(1, currentUser.id);
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				int senderid = rs.getInt("senderid");
				int recepientid = rs.getInt("recepientid");
				double amount = rs.getDouble("amount");
				boolean completed = rs.getBoolean("completed");
				String sendingAccountName = rs.getString("sendingAccountName");
				PendingTransfer p = new PendingTransfer(senderid, recepientid, amount, completed, sendingAccountName);
				list.add(p);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return list;
	}

	/**
	 * Retrieve all users and their details in the users table.
	 * 
	 * @return a map of all users and their credentials
	 */
	public Map<String, User> getAllUserDetails() {
		Map<String, User> allUsers = new TreeMap<String, User>();
		try {
			Statement stmt = conn.createStatement();
			String query = "SELECT * FROM bankv3.users order by username;";
			ResultSet rs = stmt.executeQuery(query);

			while (rs.next()) {
				int id = rs.getInt("id");
				String name = rs.getString("username");
				String password = rs.getString("pass");
				String type = rs.getString("usertype");
				boolean confirmed = rs.getBoolean("confirmed");

				User u = new User(id, name, password, type, confirmed);
				allUsers.put(name, u);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return allUsers;
	}
	
	/**
	 * Retrieve the user details of a user based on their username
	 * @param nameOfUser
	 * @return the user details
	 */
	public User getAllUserDetailsByName(String nameOfUser) {
		try {
			String query = "SELECT * FROM bankv3.users where username = ?";
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setString(1, nameOfUser);
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				// Retrieve by column name
				int id = rs.getInt("id");
				String name = rs.getString("username");
				String password = rs.getString("pass");
				String type = rs.getString("usertype");
				boolean confirmed = rs.getBoolean("confirmed");

				User u = new User(id, name, password, type, confirmed);
				rs.close();
				return u;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public User getAllUserDetailsById(int custId) {
		try {
			String query = "SELECT * FROM bankv3.users where id = ?";
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setInt(1, custId);
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				// Retrieve by column name
				int id = rs.getInt("id");
				String name = rs.getString("username");
				String password = rs.getString("pass");
				String type = rs.getString("usertype");
				boolean confirmed = rs.getBoolean("confirmed");

				User u = new User(id, name, password, type, confirmed);
				rs.close();
				return u;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * A universal insert method where the table name, columns, and values are passed in order to be inserted
	 * @param tab the table name
	 * @param cols 
	 * @param values
	 */

	public void insert(String tab, String cols, String values) {
		try {
			String sql = "insert into bankv3." + tab + " ( " + cols + " ) values (" + values + " );";
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Create a new user
	 * @param uname their username
	 * @param pass their password
	 * @param userType what type of user they are
	 */

	public void makeNewUser(String uname, String pass, String userType) {
		try {
			String sql = "INSERT INTO bankv3.users (username, pass, usertype, confirmed) values (?,?,?,'false')";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, uname);
			pstmt.setString(2, pass);
			pstmt.setString(3, userType);
			pstmt.executeUpdate();

			System.out.print("\nYour account is pending approval!\n");
			say("Please wait for an employee to approve your account.");

		} catch (SQLException e) {
			err("\nSorry! A customer with that username already exists.  Please try another username.");
			return;
		}
	}
	
	/**
	 * A universal update method
	 * @param tab the table name
	 * @param field which field to update
	 * @param newValue what's the new value
	 * @param where the where clause
	 */
	public void update(String tab, String field, String newValue, String where) {
		try {
			String sql = "update bankv3." + tab + " set " + field + " = '" + newValue + "' where " + where + ";";
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Custom called function to display to the manager the total amount of outstanding loans in the system
	 */
	public void viewTotalOutstandingLoans() {
		if (currentUser.typeOfUser.equals("Manager")) {
			try {
				CallableStatement cs = conn.prepareCall("{ ? = call bankv3.loansOutstanding() }");
				cs.registerOutParameter(1, Types.VARCHAR);
				cs.execute();
				String result = cs.getString(1);
				say("\nThe total outstanding balance for all loans: " + result);

			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			err("\nNo permission!");
		}
	}

	/**
	 * Format a double to be rounded to 2 digits beyond the decimal
	 * 
	 * @param amount to be formatted
	 * @return the formatted amount
	 */
	public String formatBalance(double amount) {
		return String.format("%.2f", amount);
	}

	/**
	 * Retrieve number input from the user for withdrawals, transfers, deposits,
	 * payments etc.
	 * 
	 * @param prompt
	 * @param max    the maximum number than can be used
	 * @return the number input
	 */
	public double getNumber(String prompt, double max) {
		Scanner sc = new Scanner(System.in);

		for (;;) { // until they enter a valid number, keep going
			try {
				System.out.print(prompt);
				if (testing) {
					return simulateUserInputThatAreNumbers();
				}
				String s = sc.next();
				double a = Double.parseDouble(s);
				if (a > 0.0 && a <= max) {
					return a;
				} else {
					err("\nInvalid number ($" + a + "0)!" + " Please enter a number between $0.00 and $"
							+ formatBalance(max));
				}
			} catch (Exception e) {
				err("\nEnter a number only.");
			}
		}
	}

	/**
	 * Retrieve input from a certain range of values, helpful when selecting options
	 * from a Menu, where they have limited options
	 * 
	 * @param prompt
	 * @param range
	 * @return the number input
	 */
	public int getNumberInRange(String prompt, int range) {
		Scanner sc = new Scanner(System.in);

		for (;;) {
			try {
				System.out.print(prompt);
				if (testing) {
					return simulateUserInputThatAreNumbers();
				}
				String s = sc.next();
				int a = Integer.parseInt(s);

				if (a > 0 && a <= range) {
					return a;
				} else {
					err("\nPlease enter a valid number (1-" + range + ")!");
				}
			} catch (Exception e) {
				err("\nPlease enter a number only.");
			}
		}
	}

	/**
	 * View of an employee once logged in.
	 */
	public void printEmployeeMenu() {
		for (;;) {
			say("\nEmployee");
			if (currentUser == null) {
				login(); // login if not logged in
			} else {
				say("1: Logout");
				say("2: Approve or reject an account");
				say("3: View transaction logs");
				say("4: Approve a loan");
				say("5: View user account balance");
				say("6: Approve or reject a bank account");
				int a = getNumberInRange("\nChoose a number: ", 6);
				switch (a) {
				case 1:
					logout();
					return;
				case 2:
					approveNewUser();
					break;
				case 3:
					showAllTransactionLogs();
					break;
				case 4:
					approveLoan();
					break;
				case 5:
					viewCustomerAccounts();
					break;
				case 6:
					approveBankAccount();
					break;
				default:
					System.out.print("No choice submitted");
				}
			}
		}
	}

	/**
	 * View of a customer once logged in.
	 */
	public void showCustomerMenu() {
		for (;;) {
			say("\nCustomer");
			if (currentUser == null) {
				login();
			} else {
				say(" 1: Logout");
				say(" 2: View balance of an account");
				say(" 3: Withdraw from an account");
				say(" 4: Deposit to an account");
				say(" 5: Make a money transfer to another account I own");
				say(" 6: Make a money transfer to another user");
				say(" 7: Request a loan");
				say(" 8: Make payments on a loan");
				say(" 9: View incoming transfers");
				say("10: Apply for a new account\n");
				int a = getNumberInRange("Enter a number: ", 10);
				switch (a) {
				case 1:
					logout();
					return;
				case 2:
					viewBalance();
					break;
				case 3:
					withdraw();
					break;
				case 4:
					deposit();
					break;
				case 5:
					transferBetweenSameUser();
					break;
				case 6:
					transferBetweenDifferentUsers();
					break;
				case 7:
					requestALoan();
					break;
				case 8:
					makeLoanPayment();
					break;
				case 9:
					viewPendingTransfers();
					break;
				case 10:
					requestBankAccount();
					break;
				default:
					err("\nNo choice submitted\n");
				}
			}
		}
	}

	/**
	 * View of a manager once logged in.
	 */
	public void showManagerMenu() {
		for (;;) {
			say("\nManager");
			if (currentUser == null) {
				login();
			} else {
				say("1: Logout");
				say("2: Make employee accounts");
				say("3: Fire employees");
				say("4: View log of all actions");
				say("5: View total outstanding loans\n");
				int a = getNumberInRange("Enter a number: ", 5);
				switch (a) {
				case 1:
					logout();
					return;
				case 2:
					hireEmployee();
					break;
				case 3:
					fireEmployee();
					break;
				case 4:
					showAllLogs();
					break;
				case 5:
					viewTotalOutstandingLoans();
					break;
				default:
					System.out.print("No choice submitted");
				}
			}
		}
	}

	/**
	 * View of a new customer, account creation
	 */
	public void showNewCustomerMenu() {
		say("\nNew Customer");
		String uname = getUsernameOrPassword("Enter a username: ");
		String password = getUsernameOrPassword("Enter a password: ");
		makeNewUser(uname, password, "Customer"); // Unapproved by default
		logMessage(
				"A new user has attempted to register for an account: " + uname + ".  They are now waiting approval!");
	}

	/**
	 * Starting menu for anyone
	 */
	public void showStartingMenu() {
		for (;;) {
			say("\n1: New Customer");
			say("2: Existing Customer");
			say("3: Employee");
			say("4: Manager");
			say("5: Exit\n");
			int a = getNumberInRange("Enter a number: ", 5);
			switch (a) {
			case 0:
				showStartingMenu();
				break;
			case 1:
				showNewCustomerMenu();
				break;
			case 2:
				showCustomerMenu();
				break;
			case 3:
				printEmployeeMenu();
				break;
			case 4:
				showManagerMenu();
				break;
			case 5:
				System.exit(0);
				return;
			default:
				System.out.print("No choice submitted");
			}
		}
	}

	/**
	 * Prompt the customer for a loan name and it's amount, verifying that the loan
	 * request is at least a certain amount and below a certain amount. Once
	 * submitted, add the loan to the loans table, as unconfirmed.
	 */
	public void requestALoan() {
		say("Applying for loan");
		String loanReason = getUsernameOrPassword("What's it for? ");
		double loanAmount = getNumber("How much is the loan for? ", MAX_LOAN);
		while (loanAmount < MIN_LOAN) {
			System.out.println("You must request a loan greater than or equal to " + MIN_LOAN);
			loanAmount = getNumber("How much is the loan for? ", MAX_LOAN);
		}
		say("\nYour loan is now pending approval!\n");
		say("Please wait for an employee to approve your loan.\n");
		insert("loans", "id, loanname, balance, confirmed",
				"" + currentUser.id + ",'" + loanReason + "', " + loanAmount + ", false");
		logMessage("TRANS:  User " + currentUser.name + " has applied for a loan for " + loanReason
				+ ". The amount is " + loanAmount);
	}

	/**
	 * Prompt the customer to apply for a new bank account and a starting balance.
	 * Then, add the account to the accounts table as unconfirmed. Once confirmed,
	 * the account will be available for use.
	 */

	public void requestBankAccount() {
		say("\nApplying for an account");
		int randomNumber = (int) (Math.random() * 9999);
		if (randomNumber <= 1000) {
			randomNumber = randomNumber + 1000;
		}
		String accType = getUsernameOrPassword("What type account would you like to open (e.g. Checking, Savings)? ");
		String AccName = accType + " (..." + randomNumber + ")";
		String finalAccName = AccName.toLowerCase();
		double initialBalance = getNumber("How much would you like to initially deposit? ", MAX_DEPOSIT);
		System.out.print("\nYour account is now pending approval!\n");
		say("Please wait for an employee to approve your account.");
		insert("accounts", "id, accountname, balance, confirmed",
				"" + currentUser.id + ",'" + finalAccName + "', " + initialBalance + ", false");
		logMessage("User " + currentUser.name + " has applied for an account named  " + finalAccName
				+ ".  The initial deposit is " + initialBalance);
	}
	
	/**
	 * Fetch the username associated with a specific user
	 * 
	 * @param id the id of the user
	 * @return the name of the user
	 */
	public String getUserName(int id) {
		String name = null;
		try {
			String query = "SELECT username FROM bankv3.users where id = ?";
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setInt(1, id);
			pstmt.executeQuery();
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				name = rs.getString("username");
				rs.close();
				return name;
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return name;
	}
	
	/**
	 * Used to retrieve a username or password from the user
	 * 
	 * @param prompt
	 * @return the username or password
	 */
	public String getUsernameOrPassword(String prompt) {
		Scanner sc = new Scanner(System.in);
		for (;;) {
			try {
				System.out.print(prompt);
				if (testing)
					return simulateUserInput();
				final String s = sc.nextLine();
				s.replaceAll("\\W", "");
				return s;
//				s.replaceAll("\\s", "");
				// String finals = s.replaceAll("\\W", "");
////		     	if (finals.length() >= 4) {
////					return finals;
////				}
//				else {
//					err("\nMust have 4 or more characters!\n");
//				}
			} catch (Exception e) {
				say("Error" + e);
			}
		}
	}
	
	/**
	 * Pop off strings to be tested through various test cases
	 * 
	 * @return
	 */
	public String simulateUserInput() {

		if (sqlStatements.size() > 0) {
			String result = sqlStatements.get(0);
			sqlStatements.remove(0);
			return result;
		} else {
			say("Cannot remove anymore test inputs");
			System.exit(0);
		}
		return null;
	}
	
	/**
	 * Pop off numbers to be tested through various test cases
	 * 
	 * @return
	 */
	public int simulateUserInputThatAreNumbers() {
		if (sqlStatements.size() > 0) {
			String result = sqlStatements.get(0);
			int numRes = Integer.parseInt(result);
			sqlStatements.remove(0);
			return numRes;
		} else {
			say("Cannot remove anymore test inputs");
			System.exit(0);
		}
		return -1;
	}
	
	/**
	 * Prints any message in red to the console.
	 * 
	 * @param O
	 */
	public static void err(Object... O) {
		final StringBuilder b = new StringBuilder();
		for (Object o : O)
			b.append(o);
		System.err.print(b.toString() + "\n");
	}
	
	/**
	 * A variant of System.out.print, used to shorten line length, resulting in
	 * tighter code.
	 * 
	 * @param O
	 */
	public static void say(Object... O) {
		final StringBuilder b = new StringBuilder();
		for (Object o : O)
			b.append(o);
		System.out.print(b.toString() + "\n");
	}
	
	/**
	 * Balances can be stored as the 'money' type in postgresDB, so it is important to
	 * convert this amount to a regular double when reading the value into the
	 * program for whatever reason.
	 * 
	 * @param money a money amount
	 * @return the amout converted to a boolean
	 */
	public double convertMoneyToDouble(String money) {
		String s = money.replaceAll("[$,]", "");
		double d = Double.parseDouble(s);
		return d;
	}
	
	/**
	 * Set the DB to a known state
	 */
	public void initializeDB() {
		for (String s : sqlTestString()) {
			try {
				Statement stmt = conn.createStatement();
				stmt.executeUpdate(s);

			} catch (SQLException e) {
				say("Failing statement " + s);
				e.printStackTrace();
			}
		}
	}

	
	
	
	
	
	
//-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-===-=-=-=-===-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

	public void test1Check() {
		countRecordsInTable("accounts", "id = 2 AND accountname = 'savings' AND balance::numeric::float4 = 1", 1);
	}

	public void test1Input() {
		Collections.addAll(sqlStatements, "2", "c2", "A", "9", "1", "1", "1", "5");
	}
	
//-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-===-=-=-=-===-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

	public void test2Check() {
		countRecordsInTable("loans", "id = 1 AND loanname = 'Truck' AND balance::numeric::float4 = 10000", 1);
		countRecordsInTable("loans", "", 2);
	}

	public void test2Input() { // request a loan, then login as employee and approve the loan
		Collections.addAll(sqlStatements, "2", "c1", "A", "7", "Truck", "10000", "1", "3", "e5", "A", "4", "2", "2", "2","2","1","1","1","5");
	}
	
//-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-===-=-=-=-===-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=


	public void test3Check() {
		countRecordsInTable("users", "id = 8 AND username = 'Nathan'", 1);
	}

	public void test3Input() {
		Collections.addAll(sqlStatements, "1", "Nathan", "Java12", "3", "e5", "A", "2", "1", "1", "5");

	}
//-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-===-=-=-=-===-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
	public void test4Check() {
		countRecordsInTable("log", "message = 'Employee e5 viewed accounts of: c4'", 1);
	}

	public void test4Input() {
		Collections.addAll(sqlStatements, "3", "e5", "A", "5", "c4", "1", "5");
	}
	
	
	
	
	/**
	 * All statements needed to reset the database and set it to a known state with
	 * known records in each table.
	 * 
	 * @return the list of SQL statements
	 */
	public static List<String> sqlTestString() {
		List<String> list = new ArrayList<String>();
		list.add("drop table if exists bankv3.users CASCADE;");
		list.add(
				"create table bankv3.users (id int NOT NULL GENERATED ALWAYS AS IDENTITY, username varchar NOT NULL, pass varchar NOT NULL, usertype varchar, confirmed boolean, CONSTRAINT users_pkey PRIMARY KEY (id), CONSTRAINT users_username_key UNIQUE (username));");
		list.add("drop table if exists bankv3.accounts CASCADE;");
		list.add(
				"create table bankv3.accounts (id int , accountname varchar, balance money, confirmed boolean, CONSTRAINT accounts_id_fkey FOREIGN KEY (id) REFERENCES bankv3.users(id));");
		list.add("drop table if exists bankv3.pendingtransfer CASCADE;");
		list.add(
				"create table bankv3.pendingtransfer (senderid int, recepientid int, amount money, completed boolean,sendingAccountName varchar, CONSTRAINT pendingtransfer_recepientid_fkey FOREIGN KEY (recepientid) REFERENCES bankv3.users(id), CONSTRAINT pendingtransfer_senderid_fkey FOREIGN KEY (senderid) REFERENCES bankv3.users(id));");
		list.add("drop table if exists bankv3.loans CASCADE;");
		list.add(
				"create table bankv3.loans (id int, loanname varchar, balance money, confirmed boolean, CONSTRAINT loans_id_fkey FOREIGN KEY (id) REFERENCES bankv3.users(id));");
		list.add("drop table if exists bankv3.log CASCADE;");
		list.add("create table bankv3.log (dt timestamp default CURRENT_TIMESTAMP,username varchar, message varchar);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('c1', 'A' ,'Customer', true);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('c2', 'A' ,'Customer', true);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('c3', 'A' ,'Customer', true);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('c4', 'A' ,'Customer', true);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('e5', 'A' ,'Employee', true);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('e6', 'A' ,'Employee', true);");
		list.add("insert into bankv3.users (username, pass, usertype, confirmed) values('m7', 'A' ,'Manager', true);");
		list.add("insert into bankv3.accounts values(1, 'savings',  0.0, true);");
		list.add("insert into bankv3.accounts values(2, 'savings',  0.0, true);");
		list.add("insert into bankv3.accounts values(3, 'checkings',  0.0, true);");
		list.add("insert into bankv3.accounts values(4, 'savings',  50.0, true);");
		list.add("insert into bankv3.accounts values(4, 'retirement',  50.0, true);");
		list.add("insert into bankv3.accounts values(4, 'checkings',  100.0, true);");
		list.add("insert into bankv3.loans values(1, 'Car',  0.0, false);");
		list.add("insert into bankv3.loans values(2, 'House',  0.0, false);");
		list.add("insert into bankv3.loans values(3, 'Car',  0.0, false);");
		list.add("insert into bankv3.loans values(4, 'House',  50.0, false);");
		list.add("insert into bankv3.loans values(4, 'Car',  100.0, false);");
		list.add("insert into bankv3.pendingtransfer values(4, 4, 50.0, false, 'savings');");
		list.add("insert into bankv3.pendingtransfer values(3, 2, 1.0, false, 'checkings');");
		list.add(";");
		return list;
	}

}
