/**
 * A class to represent and store information about messages including author, replies, and ID numbers.
 * 
 * @author Cody Reibsome, Joshua Stemmler, Josiah Neuberger
 */

public class Message
{
	
	/**
	 * Default Constructor
	 */
	public Message () {
		id = null;
		timestamp = null;
		username = null;
		tag = null;
		isReply = false;
		repliedToUsername = null;
		contents = null;
		isPrivate = 0;
	}
	
	/**
	 * Parameterized constructor. Used on new message creation.
	 * @param message Contents of the message
	 * @param auth Author of the message
	 * @param reply Whether or not the message is a reply
	 * @param replied ID of the message it is a reply to.
	 */
	public Message(String message, String auth, boolean reply, int replied)
	{
		contents = message;
		username = auth;
		isReply = reply;
	}
	
	/**
	 * Parameterized constructor. Used when pulling information from the database.
	 * @param message Contents of the message
	 * @param auth Author of the message
	 * @param ID Identification number of the message
	 * @param replyList A list of replies to the message
	 * @param reply Whether or not the message is a reply
	 * @param replied ID of the message it is a reply to
	 */
	public Message(String message, String auth, String ID, boolean reply, int replied) {
		contents = message;
		username = auth;
		id = ID;
		isReply = reply;
	}
	
	
	/**@delete no need for this since we didn't use them at all. also she may ask seeing these why we didn't use them. I think they needed to be deleted.
	 * Accessor method. returns the contents of the message.
	 * @return message contents.
	 *//*
	public String getMessage() {
		return contents;
	}*/
	
	/**@delete no need for this since we didn't use them at all. also she may ask seeing these why we didn't use them. I think they needed to be deleted.
	 * Accessor method. returns the author of the message.
	 * @return message author.
	 *//*
	public String getAuthor() {
		return username;
	}*/
	
	/**@delete no need for this since we didn't use them at all. also she may ask seeing these why we didn't use them. I think they needed to be deleted.
	 * Accessor method. returns the ID of the message.
	 * @return message ID.
	 *//*
	public String getID() {
		return id;
	}*/
	
	/**@delete no need for this since we didn't use them at all. also she may ask seeing these why we didn't use them. I think they needed to be deleted.
	 * Accessor method. Tells whether or not the message is a reply to another message.
	 * @return true if it is a reply, false if not.
	 *//*
	public boolean isReply() {
		return isReply;
	}*/
	
	
	public int returnReplyFlag() {
		if (isReply)
			return 1;
		else
			return 0;
	}
	
	/**
	 * Mapped resources from one row of a database. Each of the following data members are
	 * 	representative of one column entry of the row. Each item's capacity is controlled
	 * 	by the table in the database. Please refer to the MessageService.TABLE_MESSAGES for
	 *  more information.  
	 *
	 */
	protected String id;
	protected java.sql.Timestamp timestamp;
	protected String username;
	protected String tag;
	protected boolean isReply;
	protected String repliedToUsername;
	protected String contents;
	protected int isPrivate;	
}
