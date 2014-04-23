//package twoogle;

import java.sql.*;

/**
 * A basic User class to control user information while the program is being accessed by an end user. All user information is eventually stored
 * in the database for static retrieval at a later date.
 * 
 * @author Cody Reibsome, Joshua Stemmler, Josiah Neuberger
 *
 */
public class User {

	/**
	 * Default Constructor
	 */
	User(){
		username = MessageService.USER_GUEST;
		password = null;
		hasProfile = 0;
		profileVisible = 1; //nothing there but it's still public.
		email = null;
		gender = null;
		aboutMeMessage = null;
		birthDate = null;
		isGuest = true; //If no name is provided then the user is a guest.
	}

	/**
	 * Constructor #2 which allows for the username and password to be set upon object creation.
	 * Does not change this user from guest because the username may have not been registered yet.
	 * 
	 * @param user Username associated with user.
	 * @param pass User's password.
	 */
	User(String user, String pass){
		username = user;
		password = pass;
		hasProfile = 0;
		profileVisible = 1; //nothing there but it's still public.
		email = null;
		gender = null;
		aboutMeMessage = null;
		birthDate = null;
		isGuest = true;
	}

	/**
	 * Checks the database for the username to see if it exists.
	 * 
	 * @param s Open statement connection to the database for running sql commands.
	 * @param tableUsers The table name within the database that the users are stored.
	 * 
	 * @return True if the username was found in the database and false otherwise.
	 */
	public boolean userExists(Statement s, String tableUsers){
		boolean r = false;

		ResultSet rs = null;


		try {
			rs = s.executeQuery("Select * FROM " + tableUsers + " where username='" + this.username + "'");	

			if (rs.next()) { //user exists already
				r = true;
			} else {
				r = false;
			}

		} catch (SQLException se) {
			System.err.println(MessageService.processSqlException(se, "User.userExists(Statement s, String tableUsers)"));
		} finally {
			MessageService.closeSqlResource(rs);
		}
		return r;
	}

	/**
	 * Returns the state information contained within this user instantiation. Not formated and contains password information.
	 * Should only be used for debugging.
	 */
	public String toString() {
		return  "\n\n(" + username + ", " + password + ", " + "hasProfile=" + hasProfile + ", profileVisible=" + profileVisible + ", " + gender + ", " + birthDate + ", " + "isGuest=" + isGuest + ")\n";
	
	}
	
	/**
	 * Returns publicly available profile information of this user in a formatted String.
	 * 
	 * @param publicFormat If true a formated version of the public profile information is returned as string. Otherwise returns the debugging version by calling toString().
	 * @return If publicFormat was true a formated version of the public profile information is returned as string. Otherwise returns the debugging version by calling toString().
	 */
	public String toString(boolean publicFormat) {
		String r = "";
		
		if (publicFormat) {
			if (hasProfile == 1) {
				r += username + "'s profile:\n";
				r += "Gender: "+ gender + "\n";
				r += "Birthday: " + birthDate + "\n";
				r += "Email: " + email + "\n";
				r += "About Me: " + aboutMeMessage + "\n";
			} else {
				r = username + " has no profile.\n";
			}
			
			return r; 
		} else {
			return toString();
		}
	}

	/**
	 *Mapped resources from one row of a database. Each of the following data members are
	 * 	representative of one column entry of the row. Each item's capacity is controlled
	 * 	by the table in the database. Please refer to the MessageService.TABLE_USERS for
	 *  more information.  
	 *
	 */
	protected String username;
	protected String password;
	protected int hasProfile; //Flag 1=true, 0=false
	protected int profileVisible; //Flag 1=true, 0=false
	protected String email; 
	protected String gender; //M=Male, F=Female
	protected String aboutMeMessage;
	protected String birthDate; // string value for birthday
	protected boolean isGuest; //boolean value that checks if guest
}
