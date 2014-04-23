import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EtchedBorder;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.sql.*;


/**
 * Provide GUI access to the commandline functions of the MessageService.
 * 
 * @author Cody Reibsome, Josh Stemmler, Josiah Neuberger
 *
 */
public class TwoogleGUI {

	private Statement s;
	private MessageService m;
	private PreparedStatement psUserInsert, psProfileUpdate, psPostMessage, psUserSubscribe;
	JFrame homePageFrame, logInFrame;

	public TwoogleGUI(MessageService ms, boolean loggedIn)
	{
		m = ms;
		s = ms.s;
		psUserInsert = ms.psUserInsert;
		psProfileUpdate = ms.psProfileUpdate;
		psPostMessage = ms.psPostMessage;
		psUserSubscribe = ms.psUserSubscribe;
		//initialize both frames so that init methods are reusable (ends with a .dispose())
		homePageFrame = new JFrame();
		logInFrame = new JFrame();
		if(loggedIn)
			initHomeScreen();
		else
			initLoginGUI();
	}

	/**
	 * Helper method to modularize closing of GUI.
	 * @param f Frame to set close operations on.
	 */
	private void initCloseOp(final JFrame f) {
		f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				if(JOptionPane.showConfirmDialog(null, "Closing GUI. Shut down the rest of the program too?", "Closing GUI...", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
					System.exit(0);
				else {
					logInFrame.dispose();
					homePageFrame.dispose();
				}
			}
		});
	}

	/**
	 * Initializes the Login Screen
	 */
	private void initLoginGUI() {
		logInFrame = new JFrame("Twoogle");
		logInFrame.setLocationRelativeTo(null);
		initCloseOp(logInFrame);
		logInFrame.setResizable(false);
		JPanel outerPan = new JPanel(new BorderLayout()); //Main container

		JPanel uPan = new JPanel(); //Nested container 1, username label/box

		uPan.add(new JLabel("Username:"));
		final JTextField user = new JTextField(20);
		uPan.add(user);

		outerPan.add(uPan, BorderLayout.NORTH); //add the username container to the outer container

		JPanel pPan = new JPanel(); //Nested container 2, password label/box

		pPan.add(new JLabel("Password:"));
		final JPasswordField pass = new JPasswordField(20);
		pPan.add(pass);

		outerPan.add(pPan, BorderLayout.CENTER); //add the password container to the outer container

		JPanel bPan = new JPanel(); //Nested container 3, buttons.

		JButton login = new JButton("Log in");
		JButton register = new JButton("Register");
		JButton guest = new JButton("Guest User");
		JButton exit = new JButton("Exit");

		login.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Action on Login button press
				User u = new User(user.getText().toLowerCase(), new String(pass.getPassword()));
				if(m.userLoginGUI(s, u, 5)) {
					user.setText("");
					pass.setText("");
					initHomeScreen();
				}
				else
					JOptionPane.showMessageDialog(null, "Username and password do not match. Please try again.", "Incorrect login info", JOptionPane.ERROR_MESSAGE);
			}
		});
		register.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				initProfile(false);
			}
		});
		guest.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Action on Login as Guest button press
				initHomeScreen();
			}
		});
		exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Action on Cancel button press (Close GUI)
				if(JOptionPane.showConfirmDialog(null, "Closing GUI. Shut down the rest of the program too?", "Closing GUI...", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
					System.exit(0);
				else {
					logInFrame.dispose();
					homePageFrame.dispose();
				}
			}
		});

		//add all of the buttons to the container.
		bPan.add(login);
		bPan.add(register);
		bPan.add(guest);
		bPan.add(exit);

		outerPan.add(bPan, BorderLayout.SOUTH); //add button container to outer container

		logInFrame.add(outerPan); //add outer container to frame.
		logInFrame.pack();
		homePageFrame.dispose();
		logInFrame.setVisible(true);
	}

	/**
	 * Creates the Register Popup Dialog
	 */
	private void initProfile(boolean edit) {

		final JDialog regFrame = new JDialog(logInFrame, true); //new window opens for registration

		JPanel outerPan = new JPanel();
		outerPan.setLayout(new BoxLayout(outerPan, BoxLayout.Y_AXIS));
		outerPan.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		JPanel upPan = new JPanel(new BorderLayout());
		JPanel uPan = new JPanel();

		uPan.add(new JLabel("Username:"));
		final JTextField user = new JTextField(20);
		uPan.add(user);

		upPan.add(uPan, BorderLayout.NORTH); //username container to userpass container

		JPanel pPan = new JPanel();

		pPan.add(new JLabel("Password:"));
		final JTextField pass = new JTextField(20);
		pPan.add(pass);

		upPan.add(pPan, BorderLayout.CENTER); //password container to userpass container

		if(!edit) { //if in edit mode, this panel will NOT be added.
			outerPan.add(upPan); //userpass container to outer container
			outerPan.add(new JSeparator());
		}

		/** Profile creation */

		JPanel profilePan = new JPanel(); //contains all profile creation components
		profilePan.setLayout(new BoxLayout(profilePan, BoxLayout.Y_AXIS));

		/** Gender and Profile visibility */

		JPanel innerProfPan = new JPanel(); //contains both sets containing ButtonGroups (gen/vis)

		JPanel genderPan = new JPanel();
		genderPan.setLayout(new BoxLayout(genderPan, BoxLayout.Y_AXIS));

		genderPan.add(new JLabel("Gender:"));
		final JRadioButton maleButton = new JRadioButton("Male");
		maleButton.setSelected(true);
		final JRadioButton femaleButton = new JRadioButton("Female");

		//add buttons to ButtonGroup (logic only)
		ButtonGroup gend = new ButtonGroup();
		gend.add(maleButton);
		gend.add(femaleButton);

		//add buttons to JPanel (physical)
		genderPan.add(maleButton);
		genderPan.add(femaleButton);

		innerProfPan.add(genderPan);
		innerProfPan.add(Box.createHorizontalStrut(80));

		JPanel visibPan = new JPanel();
		visibPan.setLayout(new BoxLayout(visibPan, 1));

		visibPan.add(new JLabel("Profile Visibility:"));
		final JRadioButton visButton = new JRadioButton("Visible to all");
		visButton.setSelected(true);
		final JRadioButton invisButton = new JRadioButton("Invisible to all");

		ButtonGroup vis = new ButtonGroup();
		vis.add(visButton);
		vis.add(invisButton);

		visibPan.add(visButton);
		visibPan.add(invisButton);

		innerProfPan.add(visibPan);

		profilePan.add(innerProfPan);

		/** Email */

		JPanel emailPan = new JPanel();

		emailPan.add(new JLabel("Email:"));
		final JTextField em = new JTextField(20);
		emailPan.add(em);

		profilePan.add(emailPan);

		/** Birthday */

		JPanel bdayPan = new JPanel();

		final JTextField month = new JTextField(2);
		final JTextField day = new JTextField(2);
		final JTextField year = new JTextField(4);

		bdayPan.add(new JLabel("Birthday:"));
		bdayPan.add(month);
		bdayPan.add(new JLabel("/"));
		bdayPan.add(day);
		bdayPan.add(new JLabel("/"));
		bdayPan.add(year);

		profilePan.add(bdayPan);

		/** About */

		JPanel aboutPan = new JPanel();

		aboutPan.add(new JLabel("About me:"));
		final JTextArea aboutMe = new JTextArea(6, 30);
		aboutMe.setLineWrap(true);
		JScrollPane scroll = new JScrollPane(aboutMe);
		aboutPan.add(scroll);

		profilePan.add(aboutPan);

		outerPan.add(profilePan);

		/** Buttons */

		JPanel bPan = new JPanel();

		if(!edit) { //if not in edit mode, add registration buttons.
			JButton register = new JButton("Register");
			JButton registerWithoutProfile = new JButton("Register (No Profile)");


			register.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					//Action taken on pressing register button
					//System.err.println("Debug: " + user.getText() + "::" + pass.getText());
					User guiUser = new User(user.getText().toLowerCase(), pass.getText());

					if(!guiUser.userExists(s, MessageService.TABLE_USERS)) {
						guiUser.aboutMeMessage = aboutMe.getText();
						guiUser.birthDate = month.getText() + "/" + day.getText() + "/" + year.getText();
						guiUser.email = em.getText();
						guiUser.gender = (maleButton.isSelected()) ? "M" : "F";
						guiUser.profileVisible = (visButton.isSelected()) ? 1 : 0;
						guiUser.hasProfile = 1;
						if(m.registerNewUser(s, psUserInsert, guiUser)) {
							/** After registering, get rid of the registration frame and log the user in */
							regFrame.dispose();
							if(m.userLoginGUI(s, guiUser, 5))
								initHomeScreen();
						} else
							JOptionPane.showMessageDialog(null, "Error registering.", "Registration error", JOptionPane.ERROR_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(null, "This username already exists. Please pick another.", "Username Taken", JOptionPane.ERROR_MESSAGE);
					}
				}
			});

			registerWithoutProfile.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					//Action taken on pressing register button
					//System.err.println("Debug: " + user.getText() + "::" + pass.getText());
					User guiUser = new User(user.getText().toLowerCase(), pass.getText());

					if (!guiUser.userExists(s, MessageService.TABLE_USERS)) {
						if(m.registerNewUser(s, psUserInsert, guiUser)) {
							/** After registering, get rid of the registration frame and log the user in */
							regFrame.dispose();
							if(m.userLoginGUI(s, guiUser, 5))
								initHomeScreen();
						} else
							JOptionPane.showMessageDialog(null, "Error registering.", "Registration error", JOptionPane.ERROR_MESSAGE);
					} else
						JOptionPane.showMessageDialog(null, "This username already exists. Please pick another.", "Username Taken", JOptionPane.ERROR_MESSAGE);
				}
			});

			bPan.add(register);
			bPan.add(registerWithoutProfile);
		}
		else { //if editing profile, show update and delete buttons, and yank profile info.
			if(m.myUser.hasProfile == 1) { //if you have a profile, pull the information and update buttons
				if(m.myUser.gender.equals("M"))
					maleButton.doClick();
				else if(m.myUser.gender.equals("F"))
					femaleButton.doClick();

				if(m.myUser.profileVisible == 1)
					visButton.doClick();
				else if(m.myUser.profileVisible == 0)
					invisButton.doClick();

				em.setText(m.myUser.email);
				String t = m.myUser.birthDate; //temp string
				month.setText(t.substring(0, t.indexOf('/'))); //set month up to first /
				t = t.substring(t.indexOf('/') + 1); // trim t to after first /
				day.setText(t.substring(0, t.indexOf('/'))); //set month up to new first /
				t = t.substring(t.indexOf('/') + 1); // trim t to after new first /
				year.setText(t); //set the rest of string to year
				aboutMe.setText(m.myUser.aboutMeMessage);
			}

			JButton update = new JButton("Update Profile");
			update.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					m.myUser.aboutMeMessage = aboutMe.getText();
					m.myUser.birthDate = month.getText() + "/" + day.getText() + "/" + year.getText();
					m.myUser.email = em.getText();
					m.myUser.gender = (maleButton.isSelected()) ? "M" : "F";
					m.myUser.hasProfile = 1;
					m.myUser.profileVisible = (visButton.isSelected()) ? 1 : 0;
					m.editProfileGUI(s, psProfileUpdate, m.myUser);
					regFrame.dispose();
				}
			});
			JButton del = new JButton("Delete Profile");
			del.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					m.myUser.username = m.myUser.username;
					m.myUser.hasProfile = 0;
					m.myUser.aboutMeMessage = null;
					m.myUser.birthDate = null;
					m.myUser.email = null;
					m.myUser.gender = null;
					m.myUser.profileVisible = 0;
					m.editProfileGUI(s, psProfileUpdate, m.myUser);
					regFrame.dispose();
				}
			});
			bPan.add(update);
			bPan.add(del);
		}

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				regFrame.dispose();
			}
		});

		bPan.add(cancel);
		outerPan.add(bPan);
		regFrame.add(outerPan);
		regFrame.setTitle("Registration");
		regFrame.setResizable(false);
		regFrame.setLocationRelativeTo(null);
		regFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		regFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				regFrame.dispose();
			}
		});
		regFrame.pack();
		regFrame.setVisible(true);
	}

	/**
	 * Initializes the main screen in the program. Displays recent posts, allows posts to be made, and so forth.
	 */
	private void initHomeScreen() {
		homePageFrame = new JFrame("Twoogle Home");
		homePageFrame.setLocationRelativeTo(null);
		homePageFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		initCloseOp(homePageFrame);
		homePageFrame.setResizable(false);
		homePageFrame.setVisible(false);

		JPanel outerPan = new JPanel();
		outerPan.setLayout(new BoxLayout(outerPan, BoxLayout.Y_AXIS));
		outerPan.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		JPanel newPostPan = new JPanel();
		try {
			newPostPan.add(new JLabel(new ImageIcon(ImageIO.read(new File("placeholder.jpg")))));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		final JTextArea newPost = new JTextArea("Format: @user #tag *private message", 6, 50);
		newPost.setLineWrap(true);
		JScrollPane scroll = new JScrollPane(newPost);
		newPostPan.add(scroll);

		JButton submitPost = new JButton("Post!");
		submitPost.setPreferredSize(new Dimension(submitPost.getPreferredSize().width, newPost.getPreferredSize().height));
		submitPost.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(newPost.getText().length() > 140) {
					JOptionPane.showMessageDialog(null, "The size limit for a post is 140 characters. The current post has " 
							+ newPost.getText().length() + " characters.", "Error", JOptionPane.ERROR_MESSAGE);
				}
				else if(newPost.getText().length() == 0 || newPost.getText().equals("Format: @user #tag *private message"))
					JOptionPane.showMessageDialog(null, "Please enter a message before submitting",
							"No message entered", JOptionPane.ERROR_MESSAGE);
				else {
					//Post message
					m.postMessage(s, psPostMessage, newPost.getText());
					newPost.setText("Format: @user #tag *private message");
					output.setText("Message successfully posted!");
				}
			}
		});
		newPostPan.add(submitPost);

		try {
			newPostPan.add(new JLabel(new ImageIcon(ImageIO.read(new File("placeholder.jpg")))));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		outerPan.add(newPostPan);

		/** Output Panel */

		JPanel outputPan = new JPanel();
		output.setLineWrap(true);
		output.setEditable(false);
		output.setText(m.myUser.toString(true) + m.viewRecentMessages(s, 5));
		JScrollPane outputScroll = new JScrollPane(output);
		outputPan.add(outputScroll);

		outerPan.add(Box.createVerticalStrut(10));
		outerPan.add(outputPan);

		/** Bottom Panel */

		JPanel bottomPan = new JPanel();

		String[] options = {"View Profile", "View User Messages", "View Recent Messages",
				"View Users", "View Tags", "View Tagged Messages", "View Message by ID", 
				"View Subscribed Messages", "Subscribe to User"};			

		final JComboBox<String> selections = new JComboBox<String>(options);
		selections.setSelectedIndex(-1);
		selections.setEditable(false);

		final JTextField info = new JTextField(12);

		JButton go = new JButton("Go");
		go.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				switch (selections.getSelectedIndex()) {
				case 0:
					if(info.getText().length() == 0)
						output.setText("Please enter a user in the field below.");
					else
						output.setText(m.viewProfile(m.s, info.getText().toLowerCase()));
					break;
				case 1: 
					if(info.getText().length() == 0)
						output.setText("Please enter a user in the field below.");
					else
						output.setText(m.viewUserMessages(m.s, info.getText().toLowerCase(), 5));
					break;
				case 2:	output.setText(m.viewRecentMessages(m.s, 5));
				break;
				case 3: output.setText(m.viewUsers(m.s));
				break;
				case 4: output.setText(m.viewTags(m.s));
				break;
				case 5: 
					if(info.getText().charAt(0) != '#')
						output.setText("Please enter a tag in the field below. Format: #tag");
					else
						output.setText(m.viewMessageByTag(m.s, info.getText().toLowerCase()));
					break;
				case 6: 
					if(info.getText().length() == 0)
						output.setText("Please enter a message ID in the field below.");
					else
						output.setText(m.viewMessage(s, info.getText().toLowerCase()));
					break;
				case 7: output.setText(m.viewSubscribedToMessages(m.s, 5));
				break;
				case 8:	
					if(m.myUser.isGuest) 
						output.setText("Please log in to subscribe to other users.");
					else {
						User u = new User();
						u.username = info.getText().toLowerCase();
						//short circuited, if they don't exist subscribeToUser() won't be called.
						if(u.userExists(m.s, MessageService.TABLE_USERS) &&
								m.subscribeToUser(m.s, psUserSubscribe, info.getText().toLowerCase()))
							output.setText("Successfully subscribed to: " + info.getText().toLowerCase() + ".");
						else
							output.append("Could not subscribe to: " + info.getText().toLowerCase() + 
									", are you sure this user exists?");
					}
				}
			}
		});

		bottomPan.add(selections);
		bottomPan.add(info);
		bottomPan.add(go);
		bottomPan.add(Box.createHorizontalStrut(275));

		if(!m.myUser.isGuest) { //if the user is not a guest, show the edit profile button
			JButton editProfile = new JButton("Edit Profile");
			editProfile.setAlignmentX(Component.BOTTOM_ALIGNMENT);
			editProfile.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					initProfile(true);
					output.setText(m.myUser.toString(true) + m.viewRecentMessages(s,  5));
				}
			});
			bottomPan.add(editProfile);
		}
		else //if they are a guest, create a box as filler so the buttons are correctly aligned still.
			bottomPan.add(Box.createHorizontalStrut(80));

		JButton logout = new JButton("Log out");
		logout.setAlignmentX(Component.BOTTOM_ALIGNMENT);
		logout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				output.setText(null); //Reset to blank output screen when a new user logs in.
				m.userLogout();
				initLoginGUI();
			}
		});

		bottomPan.add(logout);
		outerPan.add(bottomPan);


		homePageFrame.add(outerPan);
		homePageFrame.pack();
		logInFrame.dispose();
		homePageFrame.setVisible(true);
	}
	private final JTextArea output = new JTextArea(18, 75); //output area on home screen.
}
