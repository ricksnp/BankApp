package bankapp;

import org.junit.Assert;

import java.util.List;

import org.junit.*;

import com.bank.driver.Account;
import com.bank.driver.Bank;

public class BankTest {
	/**
	 * Accept incoming transfers as a customer
	 */
	
	@Test
	public void test1() {
		Bank b = new Bank();
		b.testing = true;

		b.initializeDB();
		b.test1Input();
		b.showStartingMenu();
		b.test1Check();
	}
	
	/**
	 * A customer takes out a loan, the employee approves the loan
	 */
	
	@Test
	public void test2() {
		Bank b = new Bank();
		b.testing = true;

		b.initializeDB();
		b.test2Input();
		b.showStartingMenu();
		b.test2Check();
	}
	
	@Test
	public void test3() {
		Bank b = new Bank();
		b.testing = true;

		b.initializeDB();
		b.test3Input();
		b.showStartingMenu();
		b.test3Check();
	}
	
	/**
	 * View a customer's bank accounts as an employee
	 */
	
	@Test
	public void test4() {
		Bank b = new Bank();
		b.testing = true;

		b.initializeDB();
		b.test4Input();
		b.showStartingMenu();
		b.test4Check();
	}
	
	/**
	 * Make sure we convert the postgres 'money' type to a double
	 */
	
	@Test
	public void testMoneyConversion() {
		Bank b = new Bank();
		Assert.assertEquals(2.0, b.convertMoneyToDouble("$2.00"), 0.1);
	}
	
	/**
	 * Confirm that we pull proper credentials from the database
	 */
	
	@Test
	public void testGetAccountDetailsById() {
		Bank b = new Bank();
		List<Account> a = b.getAccountDetailsById(1);
		Assert.assertEquals(null, "savings", a.get(0).accountname);
	}
	

}
