package iStewart;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.prefs.Preferences;
import java.lang.Boolean;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.vecmath.Point3d;


/**
 * StewartPlatform makes three serial connections to arduinos with adafruit stepper shields.  Each shield drives two stepper motors.
 * If a clock were drawn around the origin, The motors would be arranged at the 3:00, 7:00, and 11:00 positions.
 * Each arduino is running a variant of the Drawbot code.   This variant is designed to work in 3D.
 * 
 * Each motor gets a bobbin wound with string.  The six strings come together at a common point somewhere inside the volume 
 * formed by the motors and the floor.  Changing the string lengths in the right ratio will move the end effector to anywhere within
 * the volume.
 * @author danroyer
 *
 */
public class StewartPlatform
extends JPanel
implements ActionListener, SerialConnectionReadyListener
{
	static final long serialVersionUID=1;
	
	static JFrame mainframe;
	static StewartPlatform singleton=null;
	
	// menus
	private JMenuBar menuBar;
	private JMenuItem buttonOpenFile, buttonExit;
    private JMenuItem [] buttonRecent = new JMenuItem[10];
	private JMenuItem buttonRescan, buttonJogMotors, buttonDisconnect;
	private JMenuItem buttonStart, buttonPause, buttonHalt, buttonDrive;
	private StatusBar statusBar;
	
	// serial connections
	private SerialConnection connectionBerlin;
	private SerialConnection connectionTokyo;
	private SerialConnection connectionCenterCamp;
	private boolean aReady=false, bReady=false, cReady=false, wasConfirmed=false;

	// settings
	private Preferences prefs;
	private String[] recentFiles = {"","","","","","","","","",""};
	private boolean m1invert=false,
					m2invert=false,
					m3invert=false,
					m4invert=false,
					m5invert=false,
					m6invert=false;
	
	// model
	Point3d m1=new Point3d();
	Point3d m2=new Point3d();
	Point3d m3=new Point3d();
	
	class EndEffector {
		public Point3d center=new Point3d();
		public Point3d x=new Point3d();
		public Point3d y=new Point3d();
		public Point3d z=new Point3d();
		public Point3d e1=new Point3d();
		public Point3d e2=new Point3d();
		public Point3d e3=new Point3d();
		
		public EndEffector() {
			center.set(0, 0, 0);
			x.set(1,0,0);
			y.set(0,1,0);
			z.set(0,0,1);
			e1.set(2.25*Math.sin(              0),2.25*Math.cos(              0),0);
			e2.set(2.25*Math.sin(Math.PI*1.0/3.0),2.25*Math.cos(Math.PI*1.0/3.0),0);
			e3.set(2.25*Math.sin(Math.PI*2.0/3.0),2.25*Math.cos(Math.PI*2.0/3.0),0);
		}
	}
	
	public double [] len = new double[6];
	
	private EndEffector end = new EndEffector();
	
	static final double BOBBIN_DIAMETER=0.8;
	static final double STEPS_PER_TURN=200;
	static final double STRING_PER_STEP = ( Math.PI * BOBBIN_DIAMETER ) / STEPS_PER_TURN;
	
	private int [] motor_map = new int[6];
	
	// files
	private boolean running=false;
	private boolean paused=true;
    private long linesTotal=0;
	private long linesProcessed=0;
	private boolean fileOpened=false;
	private ArrayList<String> gcode;
		
	private StewartPlatform() {
		prefs = Preferences.userRoot().node("StewartPlatform");
		
		LoadConfig();
		
		connectionBerlin = new SerialConnection("A");
		connectionTokyo = new SerialConnection("B");
		connectionCenterCamp = new SerialConnection("C");
		
		connectionBerlin.addListener(this);
		connectionTokyo.addListener(this);
		connectionCenterCamp.addListener(this);
	}
	
	static public StewartPlatform getSingleton() {
		if(singleton==null) singleton=new StewartPlatform();
		return singleton;
	}
	
	public void SerialConnectionReady(SerialConnection arg0) {
		if(arg0==connectionBerlin) aReady=true;
		if(arg0==connectionTokyo) bReady=true;
		if(arg0==connectionCenterCamp) cReady=true;
		
		if(aReady && bReady && cReady) {
			if(!wasConfirmed) {
				wasConfirmed=true;
				UpdateMenuBar();
			}
			aReady=bReady=cReady=false;
			SendFileCommand();
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();
		
		if(subject==buttonExit) {
			System.exit(0);  // @TODO: be more graceful?
			return;
		}
		if(subject==buttonOpenFile) {
			OpenFileDialog();
			return;
		}
		if(subject==buttonRescan) {
			connectionBerlin.DetectSerialPorts();
			connectionTokyo.DetectSerialPorts();
			UpdateMenuBar();
			return;
		}
		if(subject==buttonDisconnect) {
			connectionBerlin.ClosePort();
			connectionTokyo.ClosePort();
			return;
		}
		if(subject==buttonStart) {
			//if(fileOpened) OpenFile(recentFiles[0]);
			if(fileOpened) {
				paused=false;
				running=true;
				UpdateMenuBar();
				linesProcessed=0;
				//previewPane.setRunning(running);
				//previewPane.setLinesProcessed(linesProcessed);
				//statusBar.Start();
				SendFileCommand();
			}
			return;
		}
		if( subject == buttonPause ) {
			if(running) {
				if(paused==true) {
					buttonPause.setText("Pause");
					paused=false;
					// @TODO: if the robot is not ready to unpause, this might fail and the program would appear to hang.
					SendFileCommand();
				} else {
					buttonPause.setText("Unpause");
					paused=true;
				}
			}
			return;
		}
		if( subject == buttonJogMotors ) {
			JogMotors();
			return;
		}
		if( subject == buttonDrive ) {
			DriveManually();
			return;
		}
		if( subject == buttonHalt ) {
			Halt();
			return;
		}
		
		int i;
		for(i=0;i<10;++i) {
			if(subject == buttonRecent[i]) {
				OpenFile(recentFiles[i]);
				return;
			}
		}
	}
	
	public void Move(Point3d destination) {
		EndEffector start = end,temp = new EndEffector(),dest = new EndEffector();
		Point3d diff = new Point3d(destination);
		
		double distance=destination.distance(end.center);
		diff.sub(end.center);

		dest.center.set(end.center);	dest.center.add(diff);
		dest.e1.set(end.e1);			dest.e1.add(diff);
		dest.e2.set(end.e2);			dest.e2.add(diff);
		dest.e3.set(end.e3);			dest.e3.add(diff);
		
		diff.scale(distance);
		int idist = (int)Math.ceil( distance / STRING_PER_STEP );
		int i;
		double s;
		for(i=0;i<=idist;++i) {
			s=(double)i/(double)idist;
			temp.center.interpolate(start.center,dest.center,s);
			//System.out.println("temp.center="+temp.center);
			temp.e1.interpolate(start.e1,dest.e1,s);
			temp.e2.interpolate(start.e2,dest.e2,s);
			temp.e3.interpolate(start.e3,dest.e3,s);
			
			AdjustLength(0,m1.distance(temp.e1));
			AdjustLength(1,m1.distance(temp.e2));
			AdjustLength(2,m2.distance(temp.e2));
			AdjustLength(3,m2.distance(temp.e3));
			AdjustLength(4,m3.distance(temp.e3));
			AdjustLength(5,m3.distance(temp.e1));
		}
		end.center.set(dest.center);
		end.e1.set(dest.e1);
		end.e2.set(dest.e2);
		end.e3.set(dest.e3);
		
		statusBar.setMessage(end.center.toString());
	}
	
	public boolean AdjustLength(int motor_index,double newLength) {
		double d2 = newLength - len[motor_index];
		//System.out.println("d@"+motor_index+"="+d+" vs "+len[motor_index]+" = "+d2);
		if(Math.abs(d2) >= STRING_PER_STEP) {
			int direction = d2>0?1:-1;
			Step(motor_index,direction);
			len[motor_index] += direction * STRING_PER_STEP;
			return true;
		}
		return false;
	}

	public void Step(int motor_index,int direction) {
		//System.out.println("STEP "+motor_index+" "+direction);
		switch(motor_map[motor_index]) {
		case 0:  if(m1invert) direction*=-1;  connectionBerlin.    SendCommand(direction>0?"A":"B");  break;
		case 1:  if(m2invert) direction*=-1;  connectionBerlin.    SendCommand(direction>0?"C":"D");  break;
		case 2:	 if(m3invert) direction*=-1;  connectionTokyo.     SendCommand(direction>0?"A":"B");  break;
		case 3:  if(m4invert) direction*=-1;  connectionTokyo.     SendCommand(direction>0?"C":"D");  break;
		case 4:  if(m5invert) direction*=-1;  connectionCenterCamp.SendCommand(direction>0?"A":"B");  break;
		case 5:  if(m6invert) direction*=-1;  connectionCenterCamp.SendCommand(direction>0?"C":"D");  break;
		}
	}
	
	public void SineTest() {
		int i,j,steps=200;
		double x;
		
		System.out.println("SINE TEST");
		
		// setup
		System.out.println("setup");
		GoHome();
		Move(new Point3d(0,0,-3));

		double olen=len[0];
		System.out.println("OLEN "+olen);
		
		for(j=0;j<6;++j) {
			double n=olen+2.0*Math.sin(((double)j/6.0)*Math.PI*2.0);
			System.out.println("SET "+j+"="+n);
		}
		
		do {
			i=0;
			for(j=0;j<6;++j) {
				double n=olen+2.0*Math.sin(((double)j/6.0)*Math.PI*2.0);
				if(AdjustLength(j,n)) {
					i=1;
					System.out.println("NOW "+j+"="+len[j]);
				}
			}
		} while(i==1);

		// animate
		System.out.println("animate");
		for(i=0;i<steps;++i) {
			x=(double)i/(double)steps;
			for(j=0;j<6;++j) {
				AdjustLength(j,olen+2.0*Math.sin((x+(double)j/6.0))*Math.PI*2.0);
			}
		}

		// return
		System.out.println("return");
		GoHome();
	}
	
	/**
	 * stop sending commands to the robot.
	 * @todo add an e-stop command?
	 */
	public void Halt() {
		running=false;
		paused=false;
	    linesProcessed=0;
	    //previewPane.setLinesProcessed(0);
		//previewPane.setRunning(running);
		UpdateMenuBar();
	}

	private void GoHome() {
		Move(new Point3d(0,0,0));
	}
	
	/**
	 * Open the driving dialog
	 */
	public void DriveManually() {
		JDialog driver = new JDialog(mainframe,"Manual Control",true);
		driver.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		
		JButton home = new JButton("HOME");
		JButton sine = new JButton("SINE TEST");
		
		JButton up1 = new JButton("Y1");
		JButton up10 = new JButton("Y10");
		JButton up100 = new JButton("Y100");
		
		JButton down1 = new JButton("Y-1");
		JButton down10 = new JButton("Y-10");
		JButton down100 = new JButton("Y-100");
		
		JButton left1 = new JButton("X-1");
		JButton left10 = new JButton("X-10");
		JButton left100 = new JButton("X-100");
		
		JButton right1 = new JButton("X1");
		JButton right10 = new JButton("X10");
		JButton right100 = new JButton("X100");
		
		JButton in1 = new JButton("Z-1");
		JButton in10 = new JButton("Z-10");
		JButton in100 = new JButton("Z-100");
		
		JButton out1 = new JButton("Z1");
		JButton out10 = new JButton("Z10");
		JButton out100 = new JButton("Z100");
		
		JButton center = new JButton("CENTERED");
		
		c.gridx=3;	c.gridy=0;	driver.add(up100,c);
		c.gridx=3;	c.gridy=1;	driver.add(up10,c);
		c.gridx=3;	c.gridy=2;	driver.add(up1,c);
		c.gridx=3;	c.gridy=4;	driver.add(down1,c);
		c.gridx=3;	c.gridy=5;	driver.add(down10,c);
		c.gridx=3;	c.gridy=6;	driver.add(down100,c);

		c.gridx=3;	c.gridy=3;	driver.add(center,c);
		
		c.gridx=0;	c.gridy=3;	driver.add(left100,c);
		c.gridx=1;	c.gridy=3;	driver.add(left10,c);
		c.gridx=2;	c.gridy=3;	driver.add(left1,c);
		c.gridx=4;	c.gridy=3;	driver.add(right1,c);
		c.gridx=5;	c.gridy=3;	driver.add(right10,c);
		c.gridx=6;	c.gridy=3;	driver.add(right100,c);
		
		c.gridx=7;	c.gridy=0;	driver.add(out100,c);
		c.gridx=7;	c.gridy=1;	driver.add(out10,c);
		c.gridx=7;	c.gridy=2;	driver.add(out1,c);
		c.gridx=7;	c.gridy=4;	driver.add(in1,c);
		c.gridx=7;	c.gridy=5;	driver.add(in10,c);
		c.gridx=7;	c.gridy=6;	driver.add(in100,c);

		c.gridx=0;  c.gridy=6;  driver.add(home,c);
		c.gridx=0;  c.gridy=5;  driver.add(sine,c);
		
		ActionListener driveButtons = new ActionListener() {
			  public void actionPerformed(ActionEvent e) {
					Object subject = e.getSource();
					JButton b = (JButton)subject;
					String t=b.getText();
					if(t=="HOME") {
						GoHome();
					} else if(t=="CENTERED") {
						end.center.set(0,0,0);
					} else if(t=="SINE TEST") {
						SineTest();
					} else {
						Point3d target = new Point3d();
						target.set(end.center);
						double amnt = Double.parseDouble(t.substring(1));
						switch(t.charAt(0)) {
						case 'X':  target.x+=amnt;  break;
						case 'Y':  target.y+=amnt;  break;
						case 'Z':  target.z+=amnt;  break;
						}
						Move(target);
					}
			  }
			};
		
		   up1.addActionListener(driveButtons);		   up10.addActionListener(driveButtons);	   up100.addActionListener(driveButtons);
		 down1.addActionListener(driveButtons);		 down10.addActionListener(driveButtons);	 down100.addActionListener(driveButtons);
		 left1.addActionListener(driveButtons);		 left10.addActionListener(driveButtons);	 left100.addActionListener(driveButtons);
		right1.addActionListener(driveButtons);		right10.addActionListener(driveButtons);	right100.addActionListener(driveButtons);
		   in1.addActionListener(driveButtons);		   in10.addActionListener(driveButtons);	   in100.addActionListener(driveButtons);
		  out1.addActionListener(driveButtons);		  out10.addActionListener(driveButtons);	  out100.addActionListener(driveButtons);
		center.addActionListener(driveButtons);
		home.addActionListener(driveButtons);
		sine.addActionListener(driveButtons);
		driver.pack();
		driver.setVisible(true);
	}
	
	protected void JogMotors() {
		JDialog driver = new JDialog(mainframe,"Jog Motors",true);
		driver.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		
		final JButton buttonAneg = new JButton("IN");
		final JButton buttonApos = new JButton("OUT");
		final JCheckBox m1i = new JCheckBox("Invert",m1invert);
		
		final JButton buttonBneg = new JButton("IN");
		final JButton buttonBpos = new JButton("OUT");
		final JCheckBox m2i = new JCheckBox("Invert",m2invert);
		
		final JButton buttonCneg = new JButton("IN");
		final JButton buttonCpos = new JButton("OUT");
		final JCheckBox m3i = new JCheckBox("Invert",m3invert);
		
		final JButton buttonDneg = new JButton("IN");
		final JButton buttonDpos = new JButton("OUT");
		final JCheckBox m4i = new JCheckBox("Invert",m4invert);
		
		final JButton buttonEneg = new JButton("IN");
		final JButton buttonEpos = new JButton("OUT");
		final JCheckBox m5i = new JCheckBox("Invert",m5invert);
		
		final JButton buttonFneg = new JButton("IN");
		final JButton buttonFpos = new JButton("OUT");
		final JCheckBox m6i = new JCheckBox("Invert",m6invert);

		c.gridx=0;	c.gridy=0;	driver.add(new JLabel("A"),c);
		c.gridx=0;	c.gridy=1;	driver.add(new JLabel("B"),c);
		c.gridx=0;	c.gridy=2;	driver.add(new JLabel("C"),c);
		c.gridx=0;	c.gridy=3;	driver.add(new JLabel("D"),c);
		c.gridx=0;	c.gridy=4;	driver.add(new JLabel("E"),c);
		c.gridx=0;	c.gridy=5;	driver.add(new JLabel("F"),c);
		
		c.gridx=1;	c.gridy=0;	driver.add(buttonAneg,c);
		c.gridx=1;	c.gridy=1;	driver.add(buttonBneg,c);
		c.gridx=1;	c.gridy=2;	driver.add(buttonCneg,c);
		c.gridx=1;	c.gridy=3;	driver.add(buttonDneg,c);
		c.gridx=1;	c.gridy=4;	driver.add(buttonEneg,c);
		c.gridx=1;	c.gridy=5;	driver.add(buttonFneg,c);
		
		c.gridx=2;	c.gridy=0;	driver.add(buttonApos,c);
		c.gridx=2;	c.gridy=1;	driver.add(buttonBpos,c);
		c.gridx=2;	c.gridy=2;	driver.add(buttonCpos,c);
		c.gridx=2;	c.gridy=3;	driver.add(buttonDpos,c);
		c.gridx=2;	c.gridy=4;	driver.add(buttonEpos,c);
		c.gridx=2;	c.gridy=5;	driver.add(buttonFpos,c);

		c.gridx=3;	c.gridy=0;	driver.add(m1i,c);
		c.gridx=3;	c.gridy=1;	driver.add(m2i,c);
		c.gridx=3;	c.gridy=2;	driver.add(m3i,c);
		c.gridx=3;	c.gridy=3;	driver.add(m4i,c);
		c.gridx=3;	c.gridy=4;	driver.add(m5i,c);
		c.gridx=3;	c.gridy=5;	driver.add(m6i,c);
		
		ActionListener driveButtons = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object subject = e.getSource();
				if(subject == buttonApos) Step(0, 1);
				if(subject == buttonAneg) Step(0,-1);
				
				if(subject == buttonBpos) Step(1, 1);
				if(subject == buttonBneg) Step(1,-1);
				
				if(subject == buttonCpos) Step(2, 1);
				if(subject == buttonCneg) Step(2,-1);
				
				if(subject == buttonDpos) Step(3, 1);
				if(subject == buttonDneg) Step(3,-1);
				
				if(subject == buttonEpos) Step(4, 1);
				if(subject == buttonEneg) Step(4,-1);
				
				if(subject == buttonFpos) Step(5, 1);
				if(subject == buttonFneg) Step(5,-1);
			}
		};

		ActionListener invertButtons = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				m1invert = m1i.isSelected();
				m2invert = m2i.isSelected();
				m3invert = m3i.isSelected();
				m4invert = m4i.isSelected();
				m5invert = m5i.isSelected();
				m6invert = m6i.isSelected();
				
				SaveConfig();
			}
		};
		
		buttonApos.addActionListener(driveButtons);
		buttonAneg.addActionListener(driveButtons);
		
		buttonBpos.addActionListener(driveButtons);
		buttonBneg.addActionListener(driveButtons);
		
		buttonCpos.addActionListener(driveButtons);
		buttonCneg.addActionListener(driveButtons);

		buttonDpos.addActionListener(driveButtons);
		buttonDneg.addActionListener(driveButtons);

		buttonEpos.addActionListener(driveButtons);
		buttonEneg.addActionListener(driveButtons);

		buttonFpos.addActionListener(driveButtons);
		buttonFneg.addActionListener(driveButtons);

		m1i.addActionListener(invertButtons);
		m2i.addActionListener(invertButtons);
		m3i.addActionListener(invertButtons);
		m4i.addActionListener(invertButtons);
		m5i.addActionListener(invertButtons);
		m6i.addActionListener(invertButtons);
		
		driver.pack();
		driver.setVisible(true);
	}
	
	/**
	 * Take the next line from the file and send it to the robot, if permitted. 
	 */
	public void SendFileCommand() {
		if(running==false || paused==true || fileOpened==false || IsConfirmed()==false || linesProcessed>=linesTotal) return;
		
		String line;
		do {			
			// are there any more commands?
			line=gcode.get((int)linesProcessed++).trim();
			//previewPane.setLinesProcessed(linesProcessed);
			//statusBar.SetProgress(linesProcessed, linesTotal);
			// loop until we find a line that gets sent to the robot, at which point we'll
			// pause for the robot to respond.  Also stop at end of file.
		} while(!SendLineToRobot(line) && linesProcessed<linesTotal);
		
		if(linesProcessed==linesTotal) {
			// end of file
			Halt();
		}
	}
	
	/**
	 * Processes a single instruction meant for the robot.
	 * @param line
	 * @return true if the command is sent to the robot.
	 */
	public boolean SendLineToRobot(String line) {
		// tool change request?
		String [] tokens = line.split("\\s");

		// tool change?
		if(Arrays.asList(tokens).contains("M06") || Arrays.asList(tokens).contains("M6")) {
			for(int i=0;i<tokens.length;++i) {
				if(tokens[i].startsWith("T")) {
					JOptionPane.showMessageDialog(null,"Please change to tool #"+tokens[i].substring(1)+" and click OK.");
				}
			}
			// still ready to send
			return false;
		}
		
		// end of program?
		if(tokens[0]=="M02" || tokens[0]=="M2") {
			Halt();
			return false;
		}
		
		// other machine code to ignore?
		if(tokens[0].startsWith("M")) {
			//Log(line+NL);
			return false;
		} 

		// contains a comment?  if so remove it
		int index=line.indexOf('(');
		if(index!=-1) {
			//String comment=line.substring(index+1,line.lastIndexOf(')'));
			//Log("* "+comment+NL);
			line=line.substring(0,index).trim();
			if(line.length()==0) {
				// entire line was a comment.
				return false;  // still ready to send
			}
		}

		// send relevant part of line to the robot
		connectionBerlin.SendCommand(line);
		connectionTokyo.SendCommand(line);
		connectionCenterCamp.SendCommand(line);
		
		return true;
	}

	protected void LoadConfig() {
		m1.x=Double.valueOf(prefs.get("m1x", "0"));
		m1.y=Double.valueOf(prefs.get("m1y", "0"));
		m1.z=Double.valueOf(prefs.get("m1z", "0"));

		m2.x=Double.valueOf(prefs.get("m2x", "0"));
		m2.y=Double.valueOf(prefs.get("m2y", "0"));
		m2.z=Double.valueOf(prefs.get("m2z", "0"));

		m3.x=Double.valueOf(prefs.get("m3x", "0"));
		m3.y=Double.valueOf(prefs.get("m3y", "0"));
		m3.z=Double.valueOf(prefs.get("m3z", "0"));

		m1invert=Boolean.parseBoolean(prefs.get("m1invert", "false"));
		m2invert=Boolean.parseBoolean(prefs.get("m2invert", "false"));
		m3invert=Boolean.parseBoolean(prefs.get("m3invert", "false"));
		m4invert=Boolean.parseBoolean(prefs.get("m4invert", "false"));
		m5invert=Boolean.parseBoolean(prefs.get("m5invert", "false"));
		m6invert=Boolean.parseBoolean(prefs.get("m6invert", "false"));
		
		GetRecentFiles();
		
		// build the virtual model of the machine
		int i;
		for(i=0;i<6;++i) motor_map[i]=i;
		
		m1.set(25.0*Math.sin(Math.PI*1.0/6.0),25.0*Math.cos(Math.PI*1.0/6.0),20);
		m2.set(25.0*Math.sin(Math.PI*3.0/6.0),25.0*Math.cos(Math.PI*3.0/6.0),20);
		m3.set(25.0*Math.sin(Math.PI*5.0/6.0),25.0*Math.cos(Math.PI*5.0/6.0),20);
		
		len[0]=m1.distance(end.e1);
		len[1]=m1.distance(end.e2);
		len[2]=m2.distance(end.e2);
		len[3]=m2.distance(end.e3);
		len[4]=m3.distance(end.e3);
		len[5]=m3.distance(end.e1);
	}

	protected void SaveConfig() {
		prefs.put("m1x",String.valueOf(m1.x));
		prefs.put("m1y",String.valueOf(m1.y));
		prefs.put("m1z",String.valueOf(m1.z));

		prefs.put("m2x",String.valueOf(m2.x));
		prefs.put("m2y",String.valueOf(m2.y));
		prefs.put("m2z",String.valueOf(m2.z));

		prefs.put("m3x",String.valueOf(m3.x));
		prefs.put("m3y",String.valueOf(m3.y));
		prefs.put("m3z",String.valueOf(m3.z));

		prefs.put("m1invert",Boolean.toString(m1invert));
		prefs.put("m2invert",Boolean.toString(m2invert));
		prefs.put("m3invert",Boolean.toString(m3invert));
		prefs.put("m4invert",Boolean.toString(m4invert));
		prefs.put("m5invert",Boolean.toString(m5invert));
		prefs.put("m6invert",Boolean.toString(m6invert));
		
		GetRecentFiles();
	}
	
	private void CloseFile() {
		if(fileOpened==true) {
			fileOpened=false;
		}
	}
	
	/**
	 * Opens a file.  If the file can be opened, get a drawing time estimate, update recent files list, and repaint the preview tab.
	 * @param filename what file to open
	 */
	public void OpenFile(String filename) {
		CloseFile();

	    try {
	    	Scanner scanner = new Scanner(new FileInputStream(filename));
	    	linesTotal=0;
	    	gcode = new ArrayList<String>();
		    try {
		      while (scanner.hasNextLine()) {
		    	  gcode.add(scanner.nextLine());
		    	  ++linesTotal;
		      }
		    }
		    finally{
		      scanner.close();
		    }
	    }
	    catch(IOException e) {
	    	RemoveRecentFile(filename);
	    	return;
	    }
	    
	    //previewPane.setGCode(gcode);
	    fileOpened=true;
	   	UpdateRecentFiles(filename);

	   	//EstimateDrawTime();
	    Halt();
	}
	
	/**
	 * changes the order of the recent files list in the File submenu, saves the updated prefs, and refreshes the menus.
	 * @param filename the file to push to the top of the list.
	 */
	public void UpdateRecentFiles(String filename) {
		int cnt = recentFiles.length;
		String [] newFiles = new String[cnt];
		
		newFiles[0]=filename;
		
		int i,j=1;
		for(i=0;i<cnt;++i) {
			if(!filename.equals(recentFiles[i]) && recentFiles[i] != "") {
				newFiles[j++] = recentFiles[i];
				if(j == cnt ) break;
			}
		}

		recentFiles=newFiles;

		// update prefs
		for(i=0;i<cnt;++i) {
			if(!recentFiles[i].isEmpty()) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
		
		UpdateMenuBar();
	}
	
	// A file failed to load.  Remove it from recent files, refresh the menu bar.
	public void RemoveRecentFile(String filename) {
		int i;
		for(i=0;i<recentFiles.length-1;++i) {
			if(recentFiles[i]==filename) {
				break;
			}
		}
		for(;i<recentFiles.length-1;++i) {
			recentFiles[i]=recentFiles[i+1];
		}
		recentFiles[recentFiles.length-1]="";

		// update prefs
		for(i=0;i<recentFiles.length;++i) {
			if(!recentFiles[i].isEmpty()) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
		
		UpdateMenuBar();
	}
	
	// Load recent files from prefs
	public void GetRecentFiles() {
		int i;
		for(i=0;i<recentFiles.length;++i) {
			recentFiles[i] = prefs.get("recent-files-"+i, recentFiles[i]);
		}
	}
	
	// creates a file open dialog. If you don't cancel it opens that file.
	public void OpenFileDialog() {
	    // Note: source for ExampleFileFilter can be found in FileChooserDemo,
	    // under the demo/jfc directory in the Java 2 SDK, Standard Edition.
		String filename = (recentFiles[0].length()>0) ? filename=recentFiles[0] : "";

		FileFilter filterImage  = new FileNameExtensionFilter("Images (jpg/bmp/png/gif)", "jpg", "jpeg", "png", "wbmp", "bmp", "gif");
		FileFilter filterGCODE = new FileNameExtensionFilter("GCODE files (ngc)", "ngc");
		 
		JFileChooser fc = new JFileChooser(new File(filename));
		fc.addChoosableFileFilter(filterImage);
		fc.addChoosableFileFilter(filterGCODE);
	    if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
	    	OpenFile(fc.getSelectedFile().getAbsolutePath());
	    }
	}
	
	private boolean IsConfirmed() {
		return connectionBerlin.portConfirmed && connectionTokyo.portConfirmed;
	}
	
	private void UpdateMenuBar() {
		JMenu menu;
        JMenu subMenu;
        
        menuBar.removeAll();
		
        // file menu.
        menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menu);
 
        buttonOpenFile = new JMenuItem("Open File...",KeyEvent.VK_O);
        buttonOpenFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.ALT_MASK));
        buttonOpenFile.getAccessibleContext().setAccessibleDescription("Open a g-code file...");
        buttonOpenFile.addActionListener(this);
        menu.add(buttonOpenFile);

        menu.addSeparator();

        // list recent files
        GetRecentFiles();
        if(recentFiles.length>0) {
        	// list files here
        	int i;
        	for(i=0;i<recentFiles.length;++i) {
        		if(recentFiles[i].length()==0) break;
            	buttonRecent[i] = new JMenuItem((1+i) + " "+recentFiles[i],KeyEvent.VK_1+i);
            	if(buttonRecent[i]!=null) {
            		buttonRecent[i].addActionListener(this);
            		menu.add(buttonRecent[i]);
            	}
        	}
        	if(i!=0) menu.addSeparator();
        }

        buttonExit = new JMenuItem("Exit",KeyEvent.VK_Q);
        buttonExit.getAccessibleContext().setAccessibleDescription("Goodbye...");
        buttonExit.addActionListener(this);
        menu.add(buttonExit);

        menuBar.add(menu);
        
        // settings menu
        menu = new JMenu("Settings");
        menu.setMnemonic(KeyEvent.VK_T);
        menu.getAccessibleContext().setAccessibleDescription("Adjust the robot settings.");

        subMenu = connectionBerlin.getMenu();
        subMenu.setText("Arduino 1 Port");
        menu.add(subMenu);
        
        subMenu = connectionTokyo.getMenu();
        subMenu.setText("Arduino 2 Port");
        menu.add(subMenu);
        
        subMenu = connectionCenterCamp.getMenu();
        subMenu.setText("Arduino 3 Port");
        menu.add(subMenu);

        buttonRescan = new JMenuItem("Rescan Ports",KeyEvent.VK_N);
        buttonRescan.getAccessibleContext().setAccessibleDescription("Rescan the available ports.");
        buttonRescan.addActionListener(this);
        menu.add(buttonRescan);

        buttonJogMotors = new JMenuItem("Jog Motors",KeyEvent.VK_J);
        buttonJogMotors.addActionListener(this);
        menu.add(buttonJogMotors);

        menu.addSeparator();
        
        buttonDisconnect = new JMenuItem("Disconnect from arduinos",KeyEvent.VK_A);
        buttonDisconnect.addActionListener(this);
        menu.add(buttonDisconnect);
        
        menuBar.add(menu);

        // action menu
        menu = new JMenu("Action");
        menu.setMnemonic(KeyEvent.VK_A);
        menu.getAccessibleContext().setAccessibleDescription("Control robot actions.");
        menu.setEnabled(IsConfirmed());

        buttonStart = new JMenuItem("Start",KeyEvent.VK_S);
        buttonStart.getAccessibleContext().setAccessibleDescription("Start sending g-code");
        buttonStart.addActionListener(this);
    	buttonStart.setEnabled(IsConfirmed() && !running);
        menu.add(buttonStart);

        buttonPause = new JMenuItem("Pause",KeyEvent.VK_P);
        buttonPause.getAccessibleContext().setAccessibleDescription("Pause sending g-code");
        buttonPause.addActionListener(this);
        buttonPause.setEnabled(IsConfirmed() && running);
        menu.add(buttonPause);

        buttonHalt = new JMenuItem("Halt",KeyEvent.VK_H);
        buttonHalt.getAccessibleContext().setAccessibleDescription("Halt sending g-code");
        buttonHalt.addActionListener(this);
        buttonHalt.setEnabled(IsConfirmed() && running);
        menu.add(buttonHalt);

        menu.addSeparator();

        buttonDrive = new JMenuItem("Drive Manually",KeyEvent.VK_R);
        buttonDrive.getAccessibleContext().setAccessibleDescription("Etch-a-sketch style driving");
        buttonDrive.addActionListener(this);
        buttonDrive.setEnabled(IsConfirmed() && !running);
        menu.add(buttonDrive);

        menuBar.add(menu);
        
        // finish
        menuBar.updateUI();
	}
	
	public JMenuBar CreateMenuBar() {
        // If the menu bar exists, empty it.  If it doesn't exist, create it.
        menuBar = new JMenuBar();

        UpdateMenuBar();
        
        return menuBar;
	}
	
	private Container CreateContentPane() {
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setOpaque(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        JSplitPane split2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.add(connectionBerlin.getGUI());
        split2.add(connectionTokyo.getGUI());
        split2.add(connectionCenterCamp.getGUI());
        split.add(split2);
        split.setDividerSize(8);
		split.setResizeWeight(0.33);
		split.setDividerLocation(0.33);
		split2.setDividerSize(8);
		split2.setResizeWeight(0.5);
		split2.setDividerLocation(0.5);
        
        contentPane.add(split,BorderLayout.CENTER);
        
        statusBar = new StatusBar();
        contentPane.add(statusBar, java.awt.BorderLayout.SOUTH);
		statusBar.setMessage(end.center.toString());
        
        return contentPane;
	}
    
    // Create the GUI and show it.  For thread safety, this method should be invoked from the event-dispatching thread.
    private static void CreateAndShowGUI() {
        //Create and set up the window.
    	mainframe = new JFrame("Inverted Stewart Platform");
        mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
        //Create and set up the content pane.
        StewartPlatform demo = StewartPlatform.getSingleton();
        mainframe.setJMenuBar(demo.CreateMenuBar());
        mainframe.setContentPane(demo.CreateContentPane());
 
        //Display the window.
        mainframe.setSize(1100,500);
        mainframe.setVisible(true);
    }
    
    public static void main(String[] args) {
	    //Schedule a job for the event-dispatching thread:
	    //creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	            CreateAndShowGUI();
	        }
	    });
    }
}
