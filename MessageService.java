//package twoogle;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Scanner;

/**
 * A Messaging Service (MS) that allows users to posts messages to each other. Data is stored in Java's built in
 * database, Apache Derby. The MS can be run in embedded mode on a standalone computer, which allows users 
 * to take turns posting messages. The MS can also be run in client/server mode on a standalone computer, which
 * allows users to authenticate to that computer (via external security such as a vpn or ssh). In client/server
 * mode the service allows multiple users to login and post messages back and forth.
 *
 * The MS relies on User and Message classes to store information from the database while the database is being
 * accessed by users. A user can also start a GUI, which is implemented in the TwoogleGUI class.
 *
 * MessageService.java takes a command-line parameter for the main method, which controls if the enduser sees
 * debug informations and system errors such as SQL Exceptions. Otherwise, this information is stored in a
 * logfile: ie "MessageService debugmode=false" would make use of the logfile and hide information from the
 * enduser. You can also pass a command-line parameter to set the program to make a client connection to the
 * database: ie "MessageService debugmode=false connectmode=client". The Derby Network Server Service must be
 * running on the machine the database was created on (see below for instructions).
 *
 *
 * Starting MS in embedded mode: Just start MS and leave the program running. Users can choose to logon and off.
 * 
 *
 * Starting MS in client/server mode:
 * 1. Run the program in embedded mode once to create the database (or you can create a bare database with IJ manually.)
 * 2. Using a command-line set the DERBY_INSTALL environment variable to the Apache Derby base directory
 * 		which should be located within the java_jdk_7.??\db\lib\.
 * 3. Set the CLASSPATH variable to derby client mode by running the setClientDerby.bat.
 * 4. Start the Derby Network Server by running: java -jar derbyrun.jar server start
 * 5. Each user can run the program in client mode,which creates a new client connection to the Derby Network Server
 * 		and ultimately the database.
 *
 *@author Cody Reibsome, Josh Stemmler, Josiah Neuberger
 *
 */
public class MessageService {

	public static void main(String[] args) {
		boolean isEmbedded = true;

		//We need to hide debugging errors if the system is not being run in debugging mode before we do anything.
		for(String s: args) {
			int x = s.indexOf('=');
			if (x != -1 && x > 1 && x < s.length()) { //-1 indicates a '=' sign was not found, which means bogus parameter was given and can be ignored.
				String optionName = s.substring(0, x);
				String optionValue = s.substring(x + 1, s.length());

				switch (optionName.toLowerCase()) {

				case "debugmode":
					if (optionValue.equalsIgnoreCase("true")) {
						//do nothing. Errors will be printed to the standard error stream.
					}
					else if (optionValue.equalsIgnoreCase("false")) {
						//We will redirect error output to the default logfile.
						try {
							System.setErr(new PrintStream(new FileOutputStream(MessageService.LOG_FILE, true)));
						}
						catch (FileNotFoundException fe) {
							System.out.println(fe.getMessage());
							System.exit(0);
						} 
					}
					break;
				case "connectmode": //default is embedded
					if (optionValue.equalsIgnoreCase("client")) {
						isEmbedded = false;
					}
					break;
				default:
					//run with standard options, which means the program is running in embedded mode with errors 
					//printing to standard error stream and	are not hidden from the enduser.
				}
			}
		}

		MessageService m = new MessageService("c:\\temp\\dbMessageService", isEmbedded);
		m.run();

		m.inputStream.close(); //close input stream.
		System.err.print("DEBUG: The Message Service is finished and is about to exit");
	}

	/**
	 * Default Constructor 
	 * Creates a database if necessary; otherwise, connects to said database and creates needed
	 * 	tables if they don't already exists.
	 *  Opens a statement connection for running sql commands on the database. Opens and compiles prepared
	 *  statements for several regularly used sql commands such as posting messages.
	 */
	MessageService (String yourDatabase, boolean isEmbedded) {

		this.isEmbedded = isEmbedded;
		myUser = new User(); //start out as a guest user.
		dbOpenStatements = new ArrayList<Statement>(); //track all open statements so they can be closed later.

		try {

			//Connect (and create if necessary) (to) the database in embedded or client/server model mode.
			if (isEmbedded)
				myConnection = DriverManager.getConnection(protocolEmbedded + yourDatabase + ";create=true");
			else
				myConnection = DriverManager.getConnection(protocolClient + yourDatabase + ";create=true");
			System.err.println("DEBUG: We have connected to the database, which was created if needed: " + myConnection.toString());

			//Open a statement connection for running sql commands on the database.
			s = myConnection.createStatement(); //this is opening a resource and must be closed.
			dbOpenStatements.add(s); //hence, why we add it to our open statements collection.


			//Create tables if they don't already exists. A table is created for users, messages, and subscriptions.

			//Table to store registered users and the system guest account.
			if (!checkIfTableExist(s, MessageService.TABLE_USERS)) { 
				s.execute("create table " + MessageService.TABLE_USERS + "(username varchar(20), password varchar(20), messagecount int, hasprofile int, profilevisible int, gender char, birthdate varchar(15), email varchar(50), aboutme varchar(100))");
				System.err.println("DEBUG: The table for users was created");
			}

			//Table to store messages for all users.
			if (!checkIfTableExist(s, MessageService.TABLE_MESSAGES)) { 
				s.execute("create table " + MessageService.TABLE_MESSAGES + "(messageid varchar(30), timestamp timestamp, username varchar(20), tag varchar(10), isreply int, repliedtousername varchar(20), contents varchar(140), isprivate int)");
				System.err.println("DEBUG: The table for messages was created");
			}

			//Table to store subscriptions of registered users.
			if (!checkIfTableExist(s, MessageService.TABLE_SUBSCRIPTIONS)) { 
				s.execute("create table " + MessageService.TABLE_SUBSCRIPTIONS + "(username varchar(20), subscribedtousername varchar(20))");
				System.err.println("DEBUG: The table for user subscriptions was created");
			}

			//Open and compile a prepared statement for inserting new users into the database.
			psUserInsert = myConnection.prepareStatement("insert into " + MessageService.TABLE_USERS + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
			dbOpenStatements.add(psUserInsert); //track resource for closing

			//Open and compile a prepared statement for updating profile information within the database.
			psProfileUpdate = myConnection.prepareStatement("update " + MessageService.TABLE_USERS + " set hasprofile=?, profilevisible=?, gender=?, birthdate=?, email=?, aboutme=? where username=?");
			dbOpenStatements.add(psProfileUpdate);

			//Open and compile a prepared statement for inserting new subscriptions into the database.
			psUserSubscribe = myConnection.prepareStatement("insert into " + MessageService.TABLE_SUBSCRIPTIONS + " values (?, ?)");
			dbOpenStatements.add(psUserSubscribe);

			//Open and compile a prepared statement for inserting new or reply messages into the database.
			psPostMessage = myConnection.prepareStatement("insert into " + MessageService.TABLE_MESSAGES + " values (?, ?, ?, ?, ?, ?, ?, ?)");
			dbOpenStatements.add(psPostMessage);

			//Add system guest user.
			registerSystemGuestUser(s, psUserInsert);

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "Default Constructor for Message Service"));
		} 
	}

	/**
	 * Prints the menu and waits for the user to make a choice. Loops until the user chooses to exit the
	 * program. The user can choose to start a GUI which will take over control.
	 *
	 * The user has the following options available:
	 * 1. Start GUI
	 * 2. Login
	 * 3. Logout
	 * 4. Register
	 * 5. Update Profile
	 * 6. View a user's Profile
	 * 7. Exit Program
	 * 8. Post Message
	 * 9. View a message by message id
	 * 10. View messages by user
	 * 11. View messages by tag
	 * 12. View messages of subscribed to users
	 * 13. View Tags
	 * 14. View Users
	 * 15. Subscribe to a User
	 *
	 * This method will close all open resources associated with your connection to the database.
	 */
	public void run() {

		//Print menu and wait for user choice, which will be processed by this loop.
		try {
			String choice = "e";
			do {
				choice = getUserChoice(myUser.isGuest);

				switch (choice)  { 

				case "gui": //Load GUI
					new TwoogleGUI(this, !myUser.isGuest);
					break;
				case "l": //Login
					userLogin(s);
					break;
				case "lo": //logout
					userLogout();
					System.out.println("You have been logged out.");
					break;
				case "r": //Register
					registerNewUser(this.s, this.psUserInsert);
					break;
				case "up": //update profile
					editProfile(this.s, this.psProfileUpdate);
					break;
				case "vp": //view profile
					System.out.println(viewProfile(this.s, promptForUsername(this.s, "What username's profile would you like to view? ")));
					break;
				case "e": //Exit
					System.out.println("Thanks for using the Message Service. Have a nice day!");
					System.err.println("DEBUG: You have chosen to exit.");
					break;
				case "pm": //Post Message
					postMessage(this.s,this.psPostMessage);
					break;
				case "vum": //View User Messages
					System.out.println(viewUserMessages(this.s));
					break;
				case "vrm": //View Recent Messages
					System.out.println(viewRecentMessages(s, 5));
					break;
				case "vu": //View Users
					System.out.println(viewUsers(this.s));
					break;
				case "vt": //View Tags
					System.out.println(viewTags(this.s));
					break;
				case "vtm": //View tagged Messages
					System.out.println(viewMessageByTag(this.s));
					break;
				case "vm": //View a single message
					System.out.println(viewMessage(this.s));
					break;
				case "vsm": //View my subscribed to messages
					myConnection.setAutoCommit(false); //@debug @josiah do we need this with the changes to this method?
					System.out.println(viewSubscribedToMessages(this.s));
					myConnection.commit();
					myConnection.setAutoCommit(true);
					break;
				case "su": //Subscribe to user
					subscribeToUser(this.s, this.psUserSubscribe, promptForUsername(this.s, "What username would you like to subcribe to? "));
					break;
				default:
					System.out.println("Your choice doesn't exists. Please refer to the menu for valid choices. Nothing was done.");
					System.err.println("DEBUG: You were only suppose to pass valid menu choices to this function. Nothing was done.");
					choice = ""; //just causes the loop to prompts the user again.
				}
			} while (!choice.equals("e"));

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "newConnection(String yourDatabase)"));
		} finally {

			//Close all open statements and PreparedStatements
			for (Statement thisS: dbOpenStatements) {
				closeSqlResource(thisS);
				thisS = null;
			}

			//Close Connection
			closeSqlResource(myConnection);
			myConnection = null; //Release object so garbage collector can reclaim resources.
		}
	}

	/**
	 * Allows a registered user to subscribe to another user's messages, which also gives him access
	 * to that user's private messages. This method provides direct access to subcribing to users without
	 * prompting the user for input.
	 * 
	 * Assumes that s and psUserSubscribe are open connection statements. Checks that the provided
	 * input for the username to subscribe to is actually a registered user of the Message Service.
	 *
	 * WARNING: Assumes that the provided subscribeToUsername has been checked for registrations/existance
	 * within this Message Service's database.
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 * @param psUserSubscribe Precompiled open prepared statement to the database to insert a new subscription.
	 * @param subscribeToUsername Username of the person the current user wishes to subscribe.
	 */
	public boolean subscribeToUser(Statement s, PreparedStatement psUserSubscribe, String subscribeToUsername) {
		boolean r = false;

		try { 
			psUserSubscribe.setString(1, myUser.username);
			psUserSubscribe.setString(2, subscribeToUsername);

			psUserSubscribe.executeUpdate();
			r = true;

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "subscribeToUser(Statement s, PreparedStatement psUserSubscribe)"));
			System.err.println("The subscription may not have been added because a sql exception was generated.");
			r = false; 
		} 

		return r;
	}

	/**
	 * Prints to the standard out a single message string (message and any replies) associated
	 * with the specified message id, which the user is prompted to provide.
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewMessage(Statement s) {
		System.out.println("What is the message id? ");
		return viewMessage(s, inputStream.nextLine().toLowerCase());
	}

	/**
	 * Prints to the standard out a single message string (message and any replies) associated
	 * with the specified message id, which the user is prompted to provide.
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewMessage(Statement s, String id) {
		ResultSet rs = null;
		String m = "", r = "";
		String t = null, ru = null;

		try {
			rs = s.executeQuery("select * from " + MessageService.TABLE_MESSAGES + " where messageid='" + id + "' and isprivate=0");
			
			while(rs.next()) {
				m = "";
				
				t = rs.getString("tag");
				ru = rs.getString("repliedtousername");
				
				if (ru != null)
					m += String.format("%-30s", "<" + rs.getString("username") + " @" + rs.getString("repliedtousername") + "> ");
				else
					m += String.format("%-30s", "<" + rs.getString("username") + " @nobody> ");
					
				m += String.format("%-70s", "\"" + rs.getString("contents") + "\" ");
				
				if ( t != null) 
					m += String.format("%-10s", "[" + t + "] ");
				else
					m +=  String.format("%-10s", "[no tag] ");
				
				m += "(" + rs.getString("messageid") + ") ";
				m += "@" + rs.getTimestamp("timestamp") + "\n";
				
				r = m + r;
			
			}

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "viewMessages(Statement s)"));
		} finally {
			closeSqlResource(rs);
		}
		return r;
	}

	/**
	 * Prints to the standard out a specified amount of the most recent messages from a
	 * specified user. Prompts for the username and checks if the user is valid (registered)
	 * within this system. Prompts for the number of messages to print.
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewUserMessages(Statement s) {
		int limit = 5;
		String u;

		u = promptForUsername(s, "View messages of which username? "); //checks for valid username
		if (u != null) {
			System.out.println("How many messages would you like to display (default=last 5 messages)? ");
			limit = inputStream.nextInt();
			inputStream.nextLine(); //remove return made by user.

			return viewUserMessages(s, u, limit);
		} else {
			return "A bad username was provided and user did not try again: viewuserMessages()";
		}
	}

	/**
	 * Prints to the standard out a specified amount of the most recent messages from the 
	 * passed username. Assumes the username passed is valid (registered).
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 * @param u Username that was specified to view his/her messages. Must be valid.
	 * @param limit The # of message to print from each user subscribed to.
	 */
	public String viewUserMessages(Statement s, String username, int limit) {

		ResultSet rs = null;
		String m = "", r = "";
		String t = null, ru = null;

		try {
			User u = new User();
			u.username = username;
			if(u.userExists(s, TABLE_USERS)) {
				
				if (myUser.username.equals(u.username)) //Show private messages if the user is requesting his own messages. Otherwise, subscribes can only see private messages by viewing subscribed to messages.
					rs = s.executeQuery("select * from " + MessageService.TABLE_MESSAGES + " where username='" + username + "' order by timestamp desc");
				else 
					rs = s.executeQuery("select * from " + MessageService.TABLE_MESSAGES + " where username='" + username + "' and isprivate=0 order by timestamp desc");
				
				int count = 1;
				while(rs.next() && count <= limit) {
					count++;
					m = "";
					
					t = rs.getString("tag");
					ru = rs.getString("repliedtousername");
					
					if (ru != null)
						m += String.format("%-30s", "<" + rs.getString("username") + " @" + rs.getString("repliedtousername") + "> ");
					else
						m += String.format("%-30s", "<" + rs.getString("username") + " @nobody> ");
						
					m += String.format("%-70s", "\"" + rs.getString("contents") + "\" ");
					
					if ( t != null) 
						m += String.format("%-10s", "[" + t + "] ");
					else
						m +=  String.format("%-10s", "[no tag] ");
					
					m += "(" + rs.getString("messageid") + ") ";
					m += "@" + rs.getTimestamp("timestamp") + "\n";
					
					r = m + r;
				
				}
			}
			else
				return "User does not exist.";

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "viewUserMessages(Statement s)"));
		} finally {
			closeSqlResource(rs);
		}
		return r;
	}
	
	
	/**
	 * Pulls all messages of users who replied to said user.
	 * 
	 * @param s Open statement connection to the database to run sql commands.
	 * @param limit The number of messages to retrieve.
	 * @return The messages retrieved in a formatted string.
	 */
	public String viewReplyMessages(Statement s, int limit) { 
		ResultSet rs = null;
		String r = "", m = "";
		String ru = null, t = null;
		
		try {
			rs = s.executeQuery("select * from " + MessageService.TABLE_MESSAGES + " where isreply=1 and repliedtousername='" + myUser.username + "' order by timestamp desc");
			
			
			int count = 1;
			while(rs.next() && count <= limit) {
				count++;
				m = "";
				
				t = rs.getString("tag");
				ru = rs.getString("repliedtousername");
				
				if (ru != null)
					m += String.format("%-30s", "<" + rs.getString("username") + " @" + rs.getString("repliedtousername") + "> ");
				else
					m += String.format("%-30s", "<" + rs.getString("username") + " @nobody> ");
					
				m += String.format("%-70s", "\"" + rs.getString("contents") + "\" ");
				
				if ( t != null) 
					m += String.format("%-10s", "[" + t + "] ");
				else
					m +=  String.format("%-10s", "[no tag] ");
				
				m += "(" + rs.getString("messageid") + ") ";
				m += "@" + rs.getTimestamp("timestamp") + "\n";
				
				r = m + r;
			
			}
			
		} catch(SQLException se) {
			System.err.println(processSqlException(se, "viewReplyMessages(Statement s, int limit)"));
		} finally {
			closeSqlResource(rs);
		}
		return r;
	}

	/**
	 * Prints to the standard out a specified amount of the most recent messages from each of the user's subscriptions.
	 * Each message will be printed on a separate line. Prompts the user for the number of messages to print from each
	 * user she/he is subscribed to. For instance, if the user inputs 5 and is subscribed to Davis and Mike, than the 
	 * 5 most recent messages from Davis and the 5 most recent messages from Mike will be printed.
	 *
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewSubscribedToMessages(Statement s) {
		int limit = 5;

		System.out.println("How many messages from each subscribed to user would you like to display (default=last 5 messages)? ");
		limit = inputStream.nextInt();
		inputStream.nextLine(); //remove return made by user.

		return viewSubscribedToMessages(s, limit);
	}
	
	/**
	 * Prints to the standard out a specified amount of the most recent messages from each of the user's subscriptions.
	 * Each message will be printed on a separate line.
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 * @param limit The # of message to print from each user subscribed to.
	 */
	public String viewSubscribedToMessages(Statement s, int limit) {
		ResultSet rs = null;
		ResultSet rsUsers = null;
		ArrayList<String> cUsers = new ArrayList<String>();
		
		String r = "", m = "";
		String ru = null, t = null;

		try {
			//Find all usernames current user is subscribed to:
			rsUsers = s.executeQuery("select subscribedtousername from " + MessageService.TABLE_SUBSCRIPTIONS + " where username='" + myUser.username + "'");
			while(rsUsers.next()){
				cUsers.add(rsUsers.getString(1));
			}

			for (String cUser: cUsers) {	

				rs = s.executeQuery("select * from " + MessageService.TABLE_MESSAGES + " where username='" + cUser + "' order by timestamp desc");
				
				int count = 1;
				while(rs.next() && count <= limit) {
					count++;
					m = "";
					
					t = rs.getString("tag");
					ru = rs.getString("repliedtousername");
					
					if (ru != null)
						m += String.format("%-30s", "<" + rs.getString("username") + " @" + rs.getString("repliedtousername") + "> ");
					else
						m += String.format("%-30s", "<" + rs.getString("username") + " @nobody> ");
						
					m += String.format("%-70s", "\"" + rs.getString("contents") + "\" ");
					
					if ( t != null) 
						m += String.format("%-10s", "[" + t + "] ");
					else
						m +=  String.format("%-10s", "[no tag] ");
					
					m += "(" + rs.getString("messageid") + ") ";
					m += "@" + rs.getTimestamp("timestamp") + "\n";
					
					r = m + r;
				
				}
				closeSqlResource(rs); //New ResultSet for each iteration of the loop, which must be closed.
				rs = null;
			}

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "viewSubscribedToMessages(Statement s)"));
		} finally {
			closeSqlResource(rsUsers);
			closeSqlResource(rs); //This is already closed, unless a sql exception occurs, which is why it's closed here.
		}
		return r;
	}

	/**
	 * Prints to the standard out all non-private messages marked with a specified #tag.
	 * Messages will be printed with the most recent message printed to the screen last.
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewMessageByTag(Statement s)
	{
		System.out.println("Which tag do you want to search for? (Example: #oranges)");
		return viewMessageByTag(s, inputStream.nextLine().toLowerCase());
	}

	/**
	 * Prints to the standard out all non-private messages marked with a specified #tag.
	 * Messages will be printed with the most recent message printed to the screen last.
	 *
	 * Format:
	 * message_id&tag@timestamp:: "message contents"
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 * @param tag String representing the tag to search for.
	 */
	public String viewMessageByTag(Statement s, String tag) {
		//String q = "";
		
		String r = "", m = "";
		String ru = null, t = null;

		ResultSet rs = null;

		try {
			rs = s.executeQuery("select * from " + MessageService.TABLE_MESSAGES + " where tag='" + tag + "' and isprivate=0");
			/*while (rs.next()) {
				m = "";
				String t = null;
				m += rs.getString("messageid");
				t = rs.getString("tag");
				if ( t != null) 
					m += "&" + t;
				m += "@" + rs.getTimestamp("timestamp");
				m += ":: \"" + rs.getString("contents") + "\"";

				q += m + "\n";
			}*/
			

			while(rs.next()) {
				m = "";
				
				t = rs.getString("tag");
				ru = rs.getString("repliedtousername");
				
				if (ru != null)
					m += String.format("%-30s", "<" + rs.getString("username") + " @" + rs.getString("repliedtousername") + "> ");
				else
					m += String.format("%-30s", "<" + rs.getString("username") + " @nobody> ");
					
				m += String.format("%-70s", "\"" + rs.getString("contents") + "\" ");
				
				if ( t != null) 
					m += String.format("%-10s", "[" + t + "] ");
				else
					m +=  String.format("%-10s", "[no tag] ");
				
				m += "(" + rs.getString("messageid") + ") ";
				m += "@" + rs.getTimestamp("timestamp") + "\n";
				
				r = m + r;
			
			}
			
			
		} catch(SQLException se) {
			System.err.println(processSqlException(se, "viewMessageByTag(Statement s, String tag)"));
		} finally {
			closeSqlResource(rs);
		}
		return r;
	}

	/**
	 * Prints to the standard out a list of all previously used non-private #tags within this system.
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewTags(Statement s)
	{
		ResultSet rs = null;
		String tag;
		LinkedList<String> tagList = new LinkedList<String>();
		LinkedList<Integer> tagCount = new LinkedList<Integer>();
		// Count of the number of times the tag appears will be stored in the
		// same index in tagCount as the tag appears in tagList

		try {
			rs = s.executeQuery("select tag from " + MessageService.TABLE_MESSAGES + " where tag<>'null' and isprivate=0");

			while(rs.next()) {
				tag = rs.getString(1);
				if(tagList.contains(tag)) {
					tagCount.set(tagList.indexOf(tag), tagCount.get(tagList.indexOf(tag)) + 1);
					// increment the count by 1 at the index of the tag.
				}
				else {
					tagList.add(tag);
					tagCount.add(1);
					// set the count to 1.
				}
			}

			ListIterator<String> itS = tagList.listIterator();
			ListIterator<Integer> itI = tagCount.listIterator();

			String output = "";

			output += "Format: #tag (number of times used)\n";

			while(itS.hasNext()) {
				output += itS.next() + " (" + itI.next() + ")\n";
			}
			return output;
		} catch (SQLException se) {
			System.err.println(processSqlException(se, "viewTags(Statement s)"));
			return "";
		} finally {
			closeSqlResource(rs);
		}
	}

	/**
	 * Prints to the standard out the passed user's profile information.
	 * Users are not required to make a profile.
	 * Users can also mark their profiles as private (defaults to public upon creation).
	 *
	 * User's Profile's consists of the following public information:
	 *
	 * 1. username
	 * 2. Gender
	 * 3. Birthday
	 * 4. Email Address
	 * 5. About me message
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 */
	public String viewProfile(Statement s, String profileUser) {
		ResultSet rs = null;
		User u = new User();
		u.username = profileUser;

		//Should really only pass valid registered users, but we check anyway.
		if (u.userExists(s, TABLE_USERS)) {

			try {

				rs = s.executeQuery("select * from " + MessageService.TABLE_USERS  + " where username='" +u.username + "'");
				boolean queryReturned = rs.next();

				if (queryReturned) {
					u.profileVisible = rs.getInt("profileVisible");
					u.hasProfile = rs.getInt("hasprofile");

					if((u.profileVisible == 1 || u.username.equals(myUser.username) && u.hasProfile == 1)) { //profile must not be private (unless it's his own profile) and must exists

						//Grab profile information
						u.profileVisible = rs.getInt("profilevisible");
						u.gender = rs.getString("gender");
						u.birthDate = rs.getString("birthdate");
						u.email = rs.getString("email");
						u.aboutMeMessage = rs.getString("aboutme");
						
					} else {
						return "Sorry this user either does not have a profile, or it is set to private.";
					}
				}
			} catch (SQLException se) {
				processSqlException(se, "viewProfile(Statement s, String profileUser)");
			} finally {
				closeSqlResource(rs);
			}
		}
		else {
			return "Sorry this user does not exist.";
		}
		return u.toString(true);
	}

	/**
	 * Returns a string containing a list of all registered users within this system.
	 *
	 * @param s Open statement connection to the database to run sql commands.
	 * @return string containing new-line-seperated list of users.
	 */
	public String viewUsers(Statement s)
	{
		ResultSet rs = null;
		String toRet = "";

		try {
			rs = s.executeQuery("select username from " + MessageService.TABLE_USERS);

			while (rs.next()) {	
				toRet += (rs.getString(1)) + "\n";
			}
		} catch (SQLException se) {
			System.err.println(processSqlException(se, "viewUsers(Statement s)"));
		} finally {
			closeSqlResource(rs);
		}
		return toRet;
	}

	/**
	 * Allows a registered logged in user to edit his profile information.
	 * Prompts the user for each piece of information within his profile.
	 * User can only update certain parts.
	 *
	 * @param s Open statement connection to the database for running sql commands.
	 * @param psProfileUpdate Open prepared statement to the database for updating profile information.
	 */
	public void editProfile(Statement s, PreparedStatement psProfileUpdate)
	{
		boolean updated = false;
		if(myUser.hasProfile == 1) {
			String choice;
			do {
				System.out.println("* Press 'GEN' to edit gender.");
				System.out.println("* Press 'BDAY' to edit birthdate.");
				System.out.println("* Press 'EM' to edit email.");
				System.out.println("* Press 'MES' to edit your description.");
				System.out.println("* Press 'VIS' to edit profile visibility.");
				System.out.println("* Press 'E' to exit editing your profile.");
				choice = inputStream.nextLine();

				if(choice.equalsIgnoreCase("gen")) {
					if(getAnswer("Are you a male? "))
						myUser.gender = "M";
					else
						myUser.gender = "F";
				}
				else if(choice.equalsIgnoreCase("bday")) {
					System.out.print("Birthdate? ");
					myUser.birthDate = inputStream.nextLine();
				}
				else if(choice.equalsIgnoreCase("em")) {
					System.out.println("Email? ");
					myUser.email = inputStream.nextLine();
				}
				else if(choice.equalsIgnoreCase("mes")) {
					System.out.print("Short message about yourself: ");
					myUser.aboutMeMessage = inputStream.nextLine();
				}
				else if(choice.equalsIgnoreCase("vis")) {
					if(getAnswer("Would you like your profile to be publically visible? "))
						myUser.profileVisible = 1;
					else
						myUser.profileVisible = 0;
				}
			} while(!choice.equalsIgnoreCase("e"));
			updated = true;
		}

		else {
			if(getAnswer("You do not have a profile. Would you like to create one?")) {
				myUser.profileVisible = 1; //Profile visible to public by default.
				myUser.hasProfile = 1;

				if (getAnswer("Are you a male? "))
					myUser.gender = "M";
				else
					myUser.gender = "F";

				System.out.print("Birthdate? ");
				myUser.birthDate = inputStream.nextLine();

				System.out.print("Email? ");
				myUser.email = inputStream.nextLine();


				System.out.print("Short message about yourself: ");
				myUser.aboutMeMessage = inputStream.nextLine();
				updated = true;
			}
			else {
				System.out.println("Returning to menu.");
			}
		}
		if(updated) {
			try {
				psProfileUpdate.setInt(1, myUser.hasProfile);
				psProfileUpdate.setInt(2, myUser.profileVisible);
				psProfileUpdate.setString(3, myUser.gender);
				psProfileUpdate.setString(4, myUser.birthDate);
				psProfileUpdate.setString(5, myUser.email);
				psProfileUpdate.setString(6, myUser.aboutMeMessage);
				psProfileUpdate.setString(7, myUser.username);

				psProfileUpdate.executeUpdate();

			} catch (SQLException se) {
				System.err.println(processSqlException(se, "editProfile(Statement s, PreparedStatement psInsertUser)"));
			}
		}
	}
	
	public void editProfileGUI(Statement s, PreparedStatement psProfileUpdate, User u) {
		try {
			psProfileUpdate.setInt(1, u.hasProfile);
			psProfileUpdate.setInt(2, u.profileVisible);
			psProfileUpdate.setString(3, u.gender);
			psProfileUpdate.setString(4, u.birthDate);
			psProfileUpdate.setString(5, u.email);
			psProfileUpdate.setString(6, u.aboutMeMessage);
			psProfileUpdate.setString(7, u.username);

			psProfileUpdate.executeUpdate();

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "editProfile(Statement s, PreparedStatement psInsertUser)"));
		}
	}

	/**
	 * Lets registered and guest users post messages to the database. Prompts users for messages, which can specify a reply to another user's last message with @someusername.
	 * 		The user can also specify a grouping tag, hashtag, by using #somedescription. @username must be the very first item if specified followed by the #hashtag if specified.
	 * 		That is: [@username] [#hashtag] [*private] message contents
	 * 
	 * Please refer to MessageService.TABLE_MESSAGES for information pertaining to the database table.
	 * 
	 * @param s
	 * @param psPostMessage
	 * @return
	 */
	public boolean postMessage(Statement s, PreparedStatement psPostMessage) {

		//Start prompting user for message information
		System.out.println("Example: @david #movies *private I saw the greatest movie yesterday!");
		System.out.println("Example: @david *private I saw the greatest movie yesterday!");
		System.out.println("Example: *private I saw the greatest movie yesterday!");
		System.out.println("Example: #movies I saw the greatest movie yesterday!");
		System.out.println("Example: I saw the greatest movie yesterday!");
		System.out.println("Format: [@someuser] [#sometag] [*private] message contents");
		System.out.println("Message: ");

		return postMessage(s, psPostMessage, inputStream.nextLine());

	}

	/**
	 * Lets registered and guest users post messages to the database. Prompts users for messages, which can specify a reply to another user's last message with @someusername.
	 * 		The user can also specify a grouping tag, hashtag, by using #somedescription. @username must be the very first item if specified followed by the #hashtag if specified.
	 * 		That is: [@username] [#hashtag] [*private] message contents
	 * 
	 * Please refer to MessageService.TABLE_MESSAGES for information pertaining to the database table.
	 * 
	 * @param s
	 * @param psPostMessage
	 * @return
	 */
	public boolean postMessage(Statement s, PreparedStatement psPostMessage, String message) {

		boolean r = false;
		ResultSet rs = null;
		int mCount = 0;

		Message m = new Message();
		String [] splitMessage = null;

		splitMessage = extractMessageInfo(message);

		//Is it a reply?
		if (!splitMessage[0].equals("@null")) {
			m.isReply = true;
			m.repliedToUsername = splitMessage[0].substring(1, splitMessage[0].length()); //removes @ to get just username of parent.
			getMessageId(s, m.repliedToUsername, m); //Updates message, m, id.

		} else {
			m.isReply = false;
			m.repliedToUsername = null;
			mCount = getMessageId(s, myUser.username, m); //Updates message, m, id.
		}

		//Is the message tagged?
		if (!splitMessage[1].equals("#null"))
			m.tag = splitMessage[1];

		//Is the message private and only meant for subscribers?
		if (!splitMessage[2].equals("*null")) //*private was not supplied
			m.isPrivate = 1; //Flag 1 is private 0 is public.
		else
			m.isPrivate = 0; //*private was supplied.

		m.contents = splitMessage[3]; //store actual message;

		m.timestamp = new Timestamp(new java.util.Date().getTime());

		//Add values to prepared statement.
		try {
			psPostMessage.setString(1, m.id);
			psPostMessage.setTimestamp(2, m.timestamp);
			psPostMessage.setString(3, myUser.username); //user posting a message or a reply
			psPostMessage.setString(4, m.tag);
			psPostMessage.setInt(5, m.returnReplyFlag());
			psPostMessage.setString(6, m.repliedToUsername); //user being replied to, ie parent message to this reply message.
			psPostMessage.setString(7, m.contents);
			psPostMessage.setInt(8, m.isPrivate);

			psPostMessage.executeUpdate(); //content pushed to database.


			//Now we must update the value of the latest message index count for the user.
			if (!m.isReply) {
				s.executeUpdate("update " + MessageService.TABLE_USERS + " set messagecount=" + mCount + " where username='" + myUser.username + "'");
			}

			r = true;
		} catch (SQLException se) {
			
			if (se.getSQLState().equals("22001")) {
				System.out.println("You have tried to post a message that is over the 140 character limit for this system. Please try to shorten your message.");
			} else {
				System.err.println(processSqlException(se, "postMessage(Statement s, PreparedStatement psPostMessage)"));
			}
		} finally {
			closeSqlResource(rs);
		}

		return r;
	}


	/**
	 * Prints the most recent five messages from each user he/she is subscribed to (if logged in), his most recent five messages (if logged in), the guest
	 * users most recent messages. Private messages from his subscriptions are printed also (if logged in).
	 * 
	 * @param s An open statement connection to the database for running Sql commands.'
	 * @param limit The number of recent messages you wish to show.
	 */
	public String viewRecentMessages(Statement s, int limit) {

		String toRet = "";
		if (!myUser.isGuest) {
			toRet += "My Recent Messages:\n";
			toRet += viewUserMessages(s, myUser.username, limit);
			
			toRet += "\nSubscribed To Messages:\n";
			toRet += viewSubscribedToMessages(s, limit) + "\n";
			
			toRet += "\nReplies to Me:\n";
			toRet += viewReplyMessages(s, limit);
		}

		toRet += "\nGuest Messages:\n";
		toRet += viewUserMessages(s, USER_GUEST, limit);
		return toRet;
	}

	/**
	 * Logs the current user out of the system.
	 */
	public void userLogout() {
		myUser = new User();
	}

	/**
	 * Allows a registered user to login to the MessageService via the GUI. Prints the most recent five
	 * messages from each user he/she is subscribed to, his most recent five messages, the guest
	 * users most recent messages. Private messages from his subscriptions are printed also.

	 * @param s An open statement connection to the database for running Sql commands.'
	 * @param u A User object with username and password information.
	 * @param limit The number of recent messages you wish to show.
	 */
	public boolean userLoginGUI(Statement s, User u, int limit) {

		ResultSet rs = null;
		
		if (u == null || u.username == null || u.password == null) { //no user information provided.
				return false;
		} else {

			try {
				//This query will be used later to pull profile information if the correct username was provided.
				rs = s.executeQuery("select * from " + MessageService.TABLE_USERS  + " where username='" + u.username + "'");

				//Our query above should return one row.
				//rs.next() basically moves the cursor from row 0 (just a placeholder) to the first row
				// returned, which in our case is the only row.
				boolean queryReturned = rs.next();

				//If our query did not return a result than our username is incorrect.
				//Check if our passwords don't match.
				//Either case, we need to double-back to the user to get the incorrect info.
				if (!queryReturned || !u.password.equals(rs.getString("password"))) {
					return false;
				}
				else {
					u.isGuest = false;
					u.hasProfile = rs.getInt("hasprofile"); //check for a profile.
						
					//CHeck if user has a profile pull rest of his/her information.
					if (u.hasProfile == 1) { //login was successful	
	
						//Grab profile information and print it.
						u.profileVisible = rs.getInt("profilevisible");
						u.gender = rs.getString("gender");
						u.birthDate = rs.getString("birthdate");
						u.email = rs.getString("email");
						u.aboutMeMessage = rs.getString("aboutme");
					}
					myUser = u; //User is now fully logged in.
					//Print User's messages
					viewRecentMessages(s, limit);
				}

			} catch (SQLException se) {
				System.err.println(processSqlException(se, "login(Statement s)"));
			} finally {
				closeSqlResource(rs);
			}
		} //end while

		return true;
	}

	/**
	 * Allows a registered user to login to the MessageService. Prints the most recent five
	 * messages from each user he/she is subscribed to, his most recent five messages, the guest
	 * users most recent messages. Private messages from his subscriptions are printed also.

	 * @param s An open statement connection to the database for running Sql commands.
	 */
	public boolean userLogin(Statement s) {
		int limit = 5;
		ResultSet rs = null;
		boolean badCredentials = true;
		int attempts = 0;

		myUser = new User();

		while(badCredentials && attempts < 3){

			if (attempts > 0)
				System.out.println("Incorrect username and/or password");

			attempts++;

			System.out.print("Username: ");
			myUser.username = inputStream.nextLine().toLowerCase();

			System.out.print("Password: ");
			myUser.password = inputStream.nextLine();

			try {
				//This query will be used later to pull profile information if the correct username was provided.
				rs = s.executeQuery("select * from " + MessageService.TABLE_USERS  + " where username='" + myUser.username + "'");

				//Our query above should return one row.
				//rs.next() basically moves the cursor from row 0 (just a placeholder) to the first row
				// returned, which in our case is the only row.
				boolean queryReturned = rs.next();

				//If our query did not return a result than our username is incorrect.
				//Check if our passwords don't match.
				//Either case, we need to double-back to the user to get the incorrect info.
				if (!queryReturned || !myUser.password.equals(rs.getString("password"))) {
					badCredentials = true;
				}
				else {
					badCredentials = false; //good username and password
					myUser.isGuest = false;
					myUser.hasProfile = rs.getInt("hasprofile"); //check for a profile.
				

					//The login was a success and user has a profile pull rest of his/her information.
					if (myUser.hasProfile == 1) { //login was successful	
	
						//Grab profile information and print it.
						myUser.profileVisible = rs.getInt("profilevisible");
						myUser.gender = rs.getString("gender");
						myUser.birthDate = rs.getString("birthdate");
						myUser.email = rs.getString("email");
						myUser.aboutMeMessage = rs.getString("aboutme");
						System.out.println(myUser.toString(true));
					}
					//Print User's messages
					System.out.println(viewRecentMessages(s, limit));
				
				}

			} catch (SQLException se) {
				System.err.println(processSqlException(se, "login(Statement s)"));
			} finally {
				closeSqlResource(rs);
			}
		} //end while

		return myUser.isGuest==false; //if the user is still a guest then login failed and this will return false. 
	}

	/**
	 * Prompts user for input to create a User object and prompts for profile information if the user 
	 * 	so desires to create a profile. This method also adds the information to the database.
	 * 
	 * Checks to see if the username already is in use. Checks to see the username only
	 * contains letters (no numbers or special characters).
	 *  
	 * @param s - An open statement connection to the database for running sql commands
	 * @param psInsertUser - an open preparedstatement for inserting into the users table of the database.
	 * @return - true if the user didn't already exists, if the username didn't already exists, and if no sql
	 * 			exception was thrown when adding the user to the database.
	 */
	public boolean registerNewUser(Statement s, PreparedStatement psInsertUser) {
		User u = new User();

		//Start prompting user for information.
		System.out.print("Username? ");
		u.username = inputStream.nextLine().toLowerCase();

		//Check if the user already exist or if the username is already in use.

		//@josiah @debug
		while (u.userExists(s, MessageService.TABLE_USERS) ) { //@josiah @debug || u.username.contains(check for charactors only
			if (getAnswer("Username already exists, would you like to choose another? ")) {
				System.out.println("username? ");
				u.username = inputStream.nextLine().toLowerCase();
			} else
				return false; //nothing else to do.
		}
		System.out.print("Password? ");
		u.password = inputStream.nextLine();

		if (getAnswer("Would you like to create a profile? ")) {

			u.hasProfile = 1;
			u.profileVisible = 1; //Profile visible to public by default.

			if (getAnswer("Are you a male? "))
				u.gender = "M";
			else
				u.gender = "F";

			System.out.print("Birthdate? ");
			u.birthDate = inputStream.nextLine();

			System.out.print("Email? ");
			u.email = inputStream.nextLine();

			System.out.print("Write a short message about yourself: ");
			u.aboutMeMessage = inputStream.nextLine();
		}
		return registerNewUser(s, psInsertUser, u);
	}


	/**
	 * Prompts user for input to create a User object and prompts for profile information if the user 
	 * 	so desires to create a profile. This method also adds the information to the database.
	 * 
	 * Checks to see if the username already is in use. Checks to see the username only
	 * contains letters (no numbers or special characters). @debug implement this
	 * 
	 * @param s - An open statement connection to the database for running sql commands.
	 * @param psInsertUser - an open preparedstatement for inserting into the users table of the database.
	 * @param u - A user object to register.
	 * @return - true if the user didn't already exists, if the username didn't already exists, and if no sql
	 * 			exception was thrown when adding the user to the database.
	 */
	public boolean registerNewUser(Statement s, PreparedStatement psInsertUser, User u) {
		boolean r = false;
		myUser = u;

		//add to database:
		try {
			psInsertUser.setString(1, myUser.username);
			psInsertUser.setString(2, myUser.password);
			psInsertUser.setInt(3, 0); //New users have not posted any messages.
			psInsertUser.setInt(4, myUser.hasProfile);
			psInsertUser.setInt(5, myUser.profileVisible);
			psInsertUser.setString(6, myUser.gender);
			psInsertUser.setString(7, myUser.birthDate);
			psInsertUser.setString(8, myUser.email);
			psInsertUser.setString(9, myUser.aboutMeMessage);

			psInsertUser.executeUpdate();

			r = true; //user was added.

			myUser.isGuest = false; //no longer a guest.
		} catch (SQLException se) {
			r = false;
			System.err.println(processSqlException(se, "registerNewUser(Statement s, PreparedStatement psInsertUser)"));
		}
		return r;
	}

	/**
	 * Registers a system guest account for tracking unregistered postings.
	 * The name of the registered guest is controlled by the variable
	 * constant MessageService.USER_GUEST. The guest account has no profile or
	 * password associated with it.
	 *
	 * @param s An open statement connection to the database for running sql commands.
	 * @param psInsertUser An open preparedstatement connection for inserting into the users table of the database.
	 */
	private boolean registerSystemGuestUser(Statement s, PreparedStatement psInsertUser) {
		boolean r = false;

		myUser.username = MessageService.USER_GUEST;

		try {
			//Check if the system guest user has been created.
			if (!myUser.userExists(s, MessageService.TABLE_USERS)) {
				psInsertUser.setString(1, myUser.username);
				psInsertUser.setString(2, null);
				psInsertUser.setInt(3, 0); //New users have not posted any messages.
				psInsertUser.setInt(4, 0); //No profile for guest users.
				psInsertUser.setInt(5, 0);
				psInsertUser.setString(6, null);
				psInsertUser.setString(7, null);
				psInsertUser.setString(8, null);
				psInsertUser.setString(9, null);

				psInsertUser.executeUpdate();
				r = true; //added guest user.
			} else
				r = true; //user already exists.
			
		} catch (SQLException se) {
			System.err.println(processSqlException(se, "registerSystemGuestUser(Statement s, PreparedStatement psInsertUser)"));
		}
		return r;
	}

	/**
	 * Prints the a menu and prompts the user for a menu choice on the standard out. 
	 * Forces valid input from the user, which is returned in the form of a string.
	 * The menu changes depending upon if a registered user is logged into the system.
	 *
	 * Menu choices:
	 * 1. GUI
	 * 2. Login (guest only)
	 * 3. Logout (must be logged in)
	 * 4. Register (guest only)
	 * 5. Update Profile (must be logged in)
	 * 6. View a Profile (must be logged in)
	 * 7. Exit
	 * 8. Post Message
	 * 9. View a user's messages
	 * 9.5 View most recent messages (logged in or guest)
	 * 10. View a list of registered users
	 * 11. View a list of previously used tags
	 * 12. View messages tagged with #sometag
	 * 13. View a chain of messages (message and any replies)
	 * 14. Subcribe to a user's messages
	 * 15. View messages from your subscriptions
	 * 
	 * @param isGuest True if the current user of the system is a guest or not logged in, false otherwise.
	 * @return a valid menu choice.
	 */
	public String getUserChoice (boolean isGuest) {
		String input = null;

		do {
			System.out.println();
			System.out.println("**************************************************");
			System.out.println("* Press: 'GUI' to load the window driven display"); //@add @josiah
			if (isGuest) {
				System.out.println("* Press: 'L' to login");
				System.out.println("* Press: 'R' to Register");
			} else {
				System.out.println("* Press: 'LO' to logout");
				System.out.println("* Press: 'UP' to create or update your profile");
			}
			System.out.println("* Press: 'VP' to view a profile");
			System.out.println("* Press: 'E' to Exit");
			System.out.println("* Press: 'PM' to post message");
			System.out.println("* Press: 'VUM' to view a user's messages");
			System.out.println("* Press: 'VRM' to view most recent messages");
			System.out.println("* Press: 'VU' to view a list of users");
			System.out.println("* Press 'VT' to view a list of tags");
			System.out.println("* Press: 'VTM' to view messages with a tag");
			System.out.println("* Press: 'VM' to view a message by its ID");

			if (!isGuest) {
				System.out.println("* Press: 'SU' to subcribe to a user");
				System.out.println("* Press: 'VSM' to view messages from users you have subscribed to");
			}
			System.out.println("**************************************************");
			System.out.println();

			System.out.print("Menu Choice? ");
			input = inputStream.nextLine().toLowerCase();
			System.out.println();

			//Check for valid inputs for a guest/unregistered user.
			if (isGuest) {
				switch (input)  {
				case "gui":
				case "l":
				case "r":
				case "vp":
				case "e":
				case "pm":
				case "vum":
				case "vrm":
				case "vu":
				case "vt":
				case "vtm":
				case "vm":
					break; //Stop the fall through, no more valid choices.
				default:
					System.out.println("I'm sorry I don't recognize that option. Please select an option from the menu: ");
					input = null;
				}
			} else { //Check for valid inputs from a registered user.
				switch (input)  {
				case "gui":
				case "lo":
				case "up":
				case "vp":
				case "e":
				case "pm":
				case "vum":
				case "vrm":
				case "vu":
				case "vt":
				case "vtm":
				case "vm":
				case "su":
				case "vsm":
					break; //Stop the fall through, no more valid choices.
				default:
					System.out.println("I'm sorry I don't recognize that option. Please select an option from the menu: ");
					input = null;
				}
			}

		} while (input == null);

		return input; //Will return a valid choice or endlessly prompt user for one.
	}

	/**
	 * Gets an answer to a question and converts it to boolean. True is yes and false is no.
	 * Answer can be 'yes', 'y', 'n', 'no' in any case.
	 *
	 * @return A boolean representation of yes (true) and no (false).
	 */
	public boolean getAnswer (String msg) {
		String input = null;

		boolean r = false;

		if (msg != null) 
			System.out.print(msg);

		while (input == null) {
			input = inputStream.nextLine();

			switch (input.toLowerCase()) {

			case "yes":
			case "y":
				r = true;
				break;
			case "no":
			case "n":
				r = false;
				break;
			default:
				System.out.println("I'm sorry that was not a yes or no answer. Please try again!");
				input = null;
			}
		}
		return r;
	}

	/**
	 * Closes the passed SQL object, which can be a Connection, ResultSet, Statement, or PreparedStatement.
	 *
	 * @param r A object that has implemented the interface Autocloseable, though you should be passing sql objects.
	 * @throws Exception 
	 */
	public static void closeSqlResource(AutoCloseable r) {
		try {
			if (r != null) {
				r.close();
			}
		} catch (SQLException se) {
			System.err.println(processSqlException(se, "closeSqlResource(Statement r)"));
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

	/**
	 * Returns a formatted string of the information associated with the passed Sql exception.
	 * If the exception is chained, the chain will be following and appended to the returned string.
	 * 
	 * @param se Sql Exception object.
	 * @param nameOfCallingMethod String field which will be printed along with exception information.
	 * pass whatever is helpful to track where and why the exception was thrown.
	 */
	public static String processSqlException (SQLException se, String nameOfCallingMethod) {
		String r = "";

		while (se != null) {

			r += "\n*******************SQL Exception from " + nameOfCallingMethod + " **********************\n";
			r += "* SQL State:  " + se.getSQLState() + "\n";
			r += "* Error Code: " + se.getErrorCode() + "\n";
			r += "* Message:    " + se.getMessage() + "\n";
			r += "******************* Break in SQL Exception *********************************************\n";

			se = se.getNextException();
		}
		return r;
	}

	/**
	 * Prompts for a username and checks if it is registered in the system. If the username 
	 * is valid (registered) than the name is returned. Otherwise, null is returned.
	 *
	 * @param s Open statement connenction to the database to run sql commands.
	 * @param promptMessage A message to be used as a prompt to the user for requesting a username.
	 */
	public String promptForUsername (Statement s, String promptMessage) {
		User tempUser;
		boolean userExists = false;

		do {
			tempUser = new User();

			System.out.println(promptMessage);
			tempUser.username = inputStream.nextLine().toLowerCase();

			userExists = tempUser.userExists(s, MessageService.TABLE_USERS);
		} while (!userExists && getAnswer("Im sorry that username is not registered in our system. Try again? "));

		if (userExists)
			return tempUser.username;
		else {
			return null;
		}
	}

	/**
	 * Get's the current message index for the registered user or the system built in guest and increments it by one. The username and the new message index is returned as the message id.
	 * 		If the message is a reply it retains the message id of the replied to user's last message, or the parent message. In this case, the message index will not be incremented.
	 * 
	 * Example: david_34
	 * 	
	 * @param s open SQL Statement
	 * @param username Message Id based off of this username. Pass the replying username when message is a reply and not the parent username. 
	 * @param isReply - True if message is a reply to another message, false otherwise.
	 * @return Updated users message count
	 */
	private int getMessageId(Statement s, String username, Message m) {
		ResultSet rs = null;
		int index = 0;

		try {
			rs = s.executeQuery("select messagecount from " + MessageService.TABLE_USERS + " where username='" + username + "'");	

			//The sql query should have selected one row and one column and should contain exactly on result.
			boolean hasItem = rs.next();

			assert hasItem: "You should only be passing the system guest username, or a registered username"; //@cleanup do we still want asserts?

			index = rs.getInt("messagecount"); //get last message count for username.

			if (!m.isReply) //Replies keep parent id so we only need to increment for regular messages.
				index++;

			m.id = username + "_" + index;

		} catch (SQLException se) {
			System.err.println(processSqlException(se, "getMessageId(Statement s, String username, boolean isReply) "));
		} finally {
			closeSqlResource(rs);
		}

		assert m.id != null: "Some fatal error prevented the method from getting a message id"; //@cleanup do we still want asserts?

		return index;
	}

	/**
	 * Accepts a user's input message string and extracts the reply/hashtag/private information from the message.
	 * 		The information is returned in a String array of length 3:
	 * 			Index 0: @username
	 * 			Index 1: #hashtag
	 * 			Index 2: *private
	 * 			Index 3: message contents stripped of the three parts above.
	 * 
	 * Messages must be in the form:
	 * 		Reply  					 @david I want to go out for pizza
	 * 		Reply w/ hashtag	     @sarah #movies I loved that chick flic
	 * 		Reg. w/ hashtag			 #funny Knock Knock Who's there?
	 * 		Reg message				 I can't wait for Friday! 
	 * 		Reg message w/private	 *private I love the weekends.
	 * 		Reg messages w/ ht w/pri #ihatemondays *private Justin, my boss is such a prick.
	 * 
	 * @param m User's message input.
	 * @return String Array of length 4 with @username, #hashtag, *private, message contents. Uses values "@null" to indicate no reply,
	 * "#null" if no hashtag was present, and "*null" if no private was present.
	 */
	private String[] extractMessageInfo(String m) {
		int endingIndex;
		String[] r = new String[4];
		String tempM = m.trim(); //remove trailing/leading white space.

		assert m != null: "Do not pass null messages to this extract method. Handle bad input in calling class"; //@cleanup do we still want asserts?

		//Extract Reply information
		if (tempM.startsWith("@")) {
			endingIndex = tempM.indexOf(' ', 0);
			r[0] = tempM.substring(0, endingIndex);
			tempM = tempM.substring(endingIndex + 1, tempM.length()); //Remove the @user tag from beginning of message.
		} else {
			r[0] = "@null";
		}

		//Extract hashtag information
		tempM.trim();
		if (tempM.startsWith("#")) {
			endingIndex = tempM.indexOf(' ', 0);
			r[1] = tempM.substring(0, endingIndex);
			tempM = tempM.substring(endingIndex + 1, tempM.length()); //Remove the #hashname tag from beginning of message.
		} else {
			r[1] = "#null";
		}

		//Extract private information
		tempM.trim();
		if (tempM.startsWith("*")) {
			endingIndex = tempM.indexOf(' ', 0); 
			r[2] = tempM.substring(0, endingIndex);
			tempM = tempM.substring(endingIndex + 1, tempM.length()); //Remove the *private designation from beginning of message.
		} else {
			r[2] = "*null";
		}
		r[3] = tempM.trim(); //Actual message contents. Trim removes possible whitespace after removing tag/reply.

		return r; //An string array: (@username, #hashtag, *private, message contents)
	}

	/*
	 * Check if a table has already been created within the database.
	 *
	 * @param s Open statement connection to the database for running sql commands.
	 */
	private boolean checkIfTableExist(Statement s, String tableName) {
		boolean r = true;
		ResultSet rs = null;

		try {
			rs = s.executeQuery("select * from " + tableName);
		} catch (SQLException se) {
			if (se.getSQLState().equals("42X05"))
				r = false;
			else
				System.err.println(processSqlException(se, "checkIfTableExist(Connection c, String tableName)"));
		} finally {
			closeSqlResource(rs);
		}

		return r;
	}

	/**
	 * One user per instantiation of this class within a JVM.
	 *	That is, on the localhost User1@server.domain runs MessageService and
	 *	connects to the database being run in client/server mode. Another user,
	 *	user2, on the localhost user2@server.domain runs MessageService in his own JVM and
	 * 	connects to the same database. Either user could be a guest (not
	 *	registered) or a verified user (registered), regardless, myUser stores
	 * 	the user's information while the program is actively running.
	 *
	 *	If the user is registered then additional information can be pulled from the
	 *	database such as his/her profile and subscriptions.
	 */
	public User myUser; 


	//database
	private boolean isEmbedded = true; //@cleanup not used?
	private String protocolEmbedded = "jdbc:derby:";
	private String protocolClient = "jdbc:derby://localhost:1527/";

	/**
	 * Collection used to track open database resources that need to be closed before
	 * the program quits.
	 */
	ArrayList<Statement> dbOpenStatements;

	/**
	 * Variable to access the connection, which will be made to the database.
	 */
	Connection myConnection;

	/**
	 * Variable to open a statement connection to the database to run sql commmands.
	 */
	Statement s;

	/**
	 * A variable to access a prepared statement, which can be used to insert a user row into the database.
	 * See the default constructor for further information (statement connection opened and compiled there).
	 */ 
	PreparedStatement psUserInsert;
	/**
	 * A variable to access a prepared statement, which can be used to update a profile associated with a user into the database.
	 * See the default constructor for further information (statement connection opened and compiled there).
	 */ 
	PreparedStatement psProfileUpdate;
	/**
	 * A variable to access a prepared statement, which can be used to insert a message row into the database.
	 * See the default constructor for further information (statement connection opened and compiled there).
	 */ 
	PreparedStatement psPostMessage;
	/**
	 * A variable to access a prepared statement, which can be used to insert a user subscription row into the database.
	 * See the default constructor for further information (statement connection opened and compiled there).
	 */ 
	PreparedStatement psUserSubscribe;

	/**
	 *The name of the table within the database that messages are stored.
	 *
	 *All messages are stored within this database along with a username (author) tag. Unregistered/guest user' messages
	 *	are tagged with the username messageservice_guest.
	 *
	 *Registered users can mark messages as private, which are only visible to subcribers so remember to check
	 * 	this flag when pulling messages for guest users.
	 *
	 *
	 *Each message is stored in a separate row. Their information is stored in columns within that row. All columns
	 *	should be specified for every message.
	 *
	 *IE: (column #, column name) and (column name SQL datatype,...,column name SQL datatype)
	 *(1, messageid), (2, timestamp), (3, username), (4, tag), (5, isreply), (6, messagerepliedto), (7, contents), (8, isprivate)
	 *(messageid int, timestamp java.sql.timestamp, username varchar(20), tag varchar(10), isreply int, messagerepliedto int, contents varchar(140), isprivate int)
	 *
	 *SQL Example syntax:
	 *
	 * 1) "Select * from table_messages where isprivate = '0'" will return all messages visible to guest users.
	 * 2) "Select * from table_messages where username = 'David'" will return all of David's messages.
	 * 
	 *David is also subscribed to Sarah and Mike:
	 *
	 * 2) "Select * from table_messages where username = 'David' or username = 'sarah' or username = 'mike'"
	 *	we don't have to worry about the private flag because subscribers see both private and public messages.
	 */
	public static final String TABLE_MESSAGES ="table_messages";

	/**
	 *The name of the table within the database that users are stored.
	 *
	 *Each user is stored in a separate row. Their information is stored in columns within that row. Every user
	 *	within this table (all registered users) will have the first 3 columns specified.
	 *	Columns 4-8 are considered profile information and is considered optional.
	 *
	 *IE: (column #, column name) and (column name SQL datatype,...,column name SQL datatype)
	 *((1, username), (2, password), (3, messagecount), (4, hasprofile), (5, profilevisible), (6, gender), (7, birthdate), (8, email), (9, aboutme)
	 *(username varchar(20), password varchar(20), messagecount int, hasprofile int, gender char, birthdate date, email varchar(50), aboutme varchar(100)
	 *
	 *
	 *SQL Example syntax:
	 * 1) "Select * from table_users where username = 'somename'" will return a ResultSet with one row if user exists.
	 * 2) "Insert into table_users values('steve','pswd',0,1,1,'M','06/12/1983','steve@email.com','joecoolest')" insert user w/ profile.
	 *	
	 *Java SQL Equivalent syntax:
	 *
	 *Method passed Statement s, User myUser.username contains a username:
	 *
	 * 1) s.executeQuery("Select * from " + TABLE_USERS + " where username = '" + myUser.username + "'");
	 * 
	 *
	 *Precompile statement and pass it, 'ps', to a method:
	 *psUserInsert = myConnection.prepareStatement("insert into " + this.tableUsers + " values (?, ?, ?, ?, ?, ?)");
	 *
	 * 2) 
	 *		psInsertUser.setString(1, myUser.username);
	 *		psInsertUser.setString(2, myUser.password);
	 *		psInsertUser.setInteger(3, 1); //hasprofile
	 *		psInsertUser.setInteger(4, 1); //isvisible
	 *		psInsertUser.setString(5, myUser.gender);
	 *		psInsertUser.setString(6, myUser.birthDate);
	 *		psInsertUser.setString(7, myUser.email);
	 *		psInsertUser.setString(8, myUser.aboutMeMessage);
	 *		
	 *		psInsertUser.executeUpdate();
	 * 
	 */	
	public static final String TABLE_USERS = "table_users";

	/**
	 * The name of the table within the database that user subscriptions are stored.
	 * 
	 * Each subcription is stored ina separate row and specifies the user subscribing and the subscribed to user.
	 * 
	 * IE: (column #, column name) and (column name SQL datatype,...,column name SQL datatype)
	 * 	 ((1, username), (2, subcribedtousername)
	 *   (username varchar(20), subscribedtousername varchar(20) 
	 *
	 *SQL Example syntax:
	 * 1) "Select subscribedtousername from table_subscriptions where username='someusername'" will return a resultset containing one column of subcribed to usernames of user, 'somename'.
	 *
	 */
	public static final String TABLE_SUBSCRIPTIONS = "table_subscriptions";

	/**
	 * Built in account to store/track messages made by unregistered guest users.
	 */
	public static final String USER_GUEST = "messageservice_guest";

	/**
	 * Default location for redirecting the log of errors and other debugging related information.
	 */
	public static final String LOG_FILE = "c:\\temp\\MessageServiceLog.log.txt";

	/**
	 * A variable to share the standard input stream amongst many methods.
	 */
	final Scanner inputStream = new Scanner(System.in); //Standard input stream.
}