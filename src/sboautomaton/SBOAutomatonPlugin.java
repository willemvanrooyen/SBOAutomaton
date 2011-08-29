/*
 * Project SBOAutomaton
 * 
 * 2011 Starcraft Bitcoin Open <info@sc2btc.com>
 *
 * 

 */
package sboautomaton;

import hu.belicza.andras.sc2gearspluginapi.Configurable;
import hu.belicza.andras.sc2gearspluginapi.GeneralServices;
import hu.belicza.andras.sc2gearspluginapi.PluginDescriptor;
import hu.belicza.andras.sc2gearspluginapi.PluginServices;
import hu.belicza.andras.sc2gearspluginapi.SettingsControl;
import hu.belicza.andras.sc2gearspluginapi.api.LanguageApi;
import hu.belicza.andras.sc2gearspluginapi.api.SettingsApi;
import hu.belicza.andras.sc2gearspluginapi.api.listener.DiagnosticTestFactory;
import hu.belicza.andras.sc2gearspluginapi.api.listener.NewReplayListener;
import hu.belicza.andras.sc2gearspluginapi.api.sc2replay.IPlayer;
import hu.belicza.andras.sc2gearspluginapi.api.sc2replay.IReplay;
import hu.belicza.andras.sc2gearspluginapi.impl.BasePlugin;
import hu.belicza.andras.sc2gearspluginapi.impl.BaseSettingsControl;
import hu.belicza.andras.sc2gearspluginapi.impl.DiagnosticTest;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;


import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

//Willem added these
import hu.belicza.andras.sc2gearspluginapi.api.GuiUtilsApi;
import javax.swing.JDialog;
import hu.belicza.andras.sc2gearspluginapi.api.httpost.IHttpPost;
import hu.belicza.andras.sc2gearspluginapi.api.httpost.FileProvider;
import hu.belicza.andras.sc2gearspluginapi.api.sc2replay.ReplayConsts.Format;
import hu.belicza.andras.sc2gearspluginapi.api.sc2replay.ReplayConsts.GameType;
import java.util.Map;
import java.util.HashMap;
//import java.io.File;
import java.net.HttpURLConnection;
import javax.swing.JTextArea;


import org.json.JSONObject;
import org.json.JSONException;



/**
 * A plugin which enables full-automation Starcraft 2 tournaments (automated replay submission)
 * The user must set their player Id in the plugin configuration in order for it to work
 * 
 * <p>The plugin also registers a diagnostic test to check the pre-conditions of the plugin.</p>
 * 
 * @author Willem van Rooyen
 */
public class SBOAutomatonPlugin extends BasePlugin implements Configurable {
	
        
        private static final String URL_SBO_PROCESSFLOW_ISINTOURNAMENT    = "http://sc2btcopen.appspot.com/sc2gears/isintournament";
        private static final String KEY_SBO_PLAYERID    = "SBOAutomaton.playerId";
        	
        /** Reference to our new replay listener.     */
	private NewReplayListener     newReplayListener;
	
	/** Reference to our diagnostic test factory. */
	private DiagnosticTestFactory diagnosticTestFactory;
        
        //internal use for decision making/submission params
        private String matchKey = null;
        private String tournamentKey = null; //unused at the moment, server conversation returns it (for future additions)
        private String playerName = null;
        private String upload_url = null;
        	
	@Override
	public void init( final PluginDescriptor pluginDescriptor, final PluginServices pluginServices, final GeneralServices generalServices ) {
		
                // Call the init() implementation of the BasePlugin:
		super.init( pluginDescriptor, pluginServices, generalServices );
		

		// Register a diagnostic test to check the pre-conditions of this plugin:
                //used when full program diagnostic test is run
		generalServices.getCallbackApi().addDiagnosticTestFactory( diagnosticTestFactory = new DiagnosticTestFactory() {
			@Override
			public DiagnosticTest createDiagnosticTest() {                            
                            //TODO: implement language api calls
				return new DiagnosticTest( "Starcraft 2 Bitcoin Open Plugin" ) {
                                    
					@Override
					public void execute() {                                           
//
                                                if ( isActionRequired() ) {
                                                        result  = Result.WARNING;
                                                        details = "You need to set your player Id to enable usage.";
                                                }
                                                else if ( !generalServices.getInfoApi().isReplayAutoSaveEnabled() ) {
                                                        result  = Result.WARNING;
                                                        details = "Auto-save is not enabled, without it, your replays will not be submitted Stacraft 2 Bitcoin Open" ;
                                                }
                                                else {
                                                        result  = Result.OK;
                                                }
					}
				};
			}
		} );
		
		// Register a new replay listener
		generalServices.getCallbackApi().addNewReplayListener( newReplayListener = new NewReplayListener() {
			@Override
			public void newReplayDetected( final File replayFile ) {
                            
                                //TODO: use diagnostic test/wrapper to check plugin config validity
                                if (!(isStringSettingMissing( KEY_SBO_PLAYERID )))
                                {                                    
                                
                                //not using voice notifications yet; still unsure if necessary, leaving it in for after user f/b
                                //current thoughts on voice notifies is that the plugin would download its own (localised) media for use, if needed
                                //currently we're using the browser audio for user notifications, seems fine
    //				// Respect the user's setting about voice notifications:
    //				if ( !generalServices.getInfoApi().isVoiceNotificationsEnabled() )
    //					return;

                                    // Cached info is enough for us: we acquire replay with ReplayFactory.getReplay()
                                    final IReplay replay = generalServices.getReplayFactoryApi().getReplay( replayFile.getAbsolutePath(), null );
                                    if ( replay == null )
                                            return; // Failed to parse replay

                                    if(replay.getFormat()==Format.ONE_VS_ONE) //enum From ReplayConsts.Java: ONE_VS_ONE, TWO_VS_TWO, THREE_VS_THREE, FOUR_VS_FOUR, FREE_FOR_ALL, UNKNOWN;
                                    {                                    
                                        //only 1v1 for now, server-side does not yet support team play
                                        if(!(replay.getGameType()==GameType.AMM)) //enum From ReplayConsts.Java: PRIVATE, PUBLIC, AMM, SINGLE_PLAYER, UNKNOWN;
                                        {                                        
                                            //we don't want to submit ladder games (AutoMM), for example
                                            //here we start the submission workflow (check if the player is in a tournament, if so then submit etc)
                                            if (isInTournament())
                                            {
                                                //System.out.println("found a current tournamnent for this user");                                            
                                                submitReplay(replayFile.getAbsolutePath());                                            
                                            }                                    
                                        }
                                    }

                                }
                        }
		} );
	}
	
        private void submitReplay(final String replayPath)
        {        
             String url = upload_url;
            
             Map< String, String > paramsMap = new HashMap< String, String >();
             paramsMap.put( "MatchKey", matchKey );        
             paramsMap.put( "playerId", pluginServices.getSettingsApi().getString( KEY_SBO_PLAYERID ));            
            
             String SB2BTCOpenResponse = HTTPPostWrapper(url, paramsMap, replayPath);
             System.out.println( SB2BTCOpenResponse );
             //and that's it - server-side takes over from here        
        
        }        
        
        private String HTTPPostWrapper(final String url, final Map< String, String > paramsMap, final String replayPath)
        {
            //checks whether the player is currently in a tournament:
             IHttpPost httpPost = null;
             try {
                 
                 if (replayPath!=null)
                 {
                     //just add it to the parameter map
                     File file = new File( replayPath );
                     paramsMap.put( "fileBase64", generalServices.getGeneralUtilsApi().encodeFileBase64( file ) );
                 }
                 
                 
                 httpPost = generalServices.getGeneralUtilsApi().createHttpPost( url, paramsMap );
                 if ( httpPost.connect() ) {
                     if ( httpPost.doPost() ) {                                  
                         //System.out.println( "Doing the response..." );                        
                         return httpPost.getResponse();                         
                     }
                     else
                         System.out.println( "Failed to send request to SC2BTCOpen!" );
                 }
                 else
                     System.out.println( "Failed to connect to SC2BTCOpen!" );
             } catch ( Exception e ) {
                 e.printStackTrace();
             } finally {
                 if ( httpPost != null )
                     httpPost.close();
             }            
             
            return "";
        }
        
   
        private void initInTournamentVariables()
        {
            matchKey = null;
            tournamentKey = null;
            playerName = null;
            upload_url = null;
        }        
        private boolean isInTournament()
        {
            //System.out.println( "isInTournament STARTS" );
            
            String url = URL_SBO_PROCESSFLOW_ISINTOURNAMENT; 
            Map< String, String > paramsMap = new HashMap< String, String >();
           
            paramsMap.put( "playerId", pluginServices.getSettingsApi().getString( KEY_SBO_PLAYERID ));            
            //System.out.println( "playerId:" + pluginServices.getSettingsApi().getString( KEY_SBO_PLAYERID ) );
             
            String SB2BTCOpenResponse = HTTPPostWrapper(url, paramsMap, null);
            System.out.println( SB2BTCOpenResponse );
            if (SB2BTCOpenResponse=="")
            {
                // not expecting this, but just in case
                return false;
            }             
             
            try
            {
                JSONObject isInTournamentJSON;
                isInTournamentJSON = new JSONObject(SB2BTCOpenResponse);                     
                
                //TODO: not technically errors, but any of these in this context should just quit out of it
                //possible response(s) when not in tournament: 
                //{ \"error\":\"match pending (waiting for next match)\" }
                //{ \"error\":\"joined tournament, but it has not yet started\" }
                //{ \"error\":\"not currently in tournament\" }
                
                //true response e.g.: 
                //"{ \"playerName\":\"" + prefs.bnetPlayerName  + "\", \"upload_url\":\"" + upload_url +"\" , \"tournamentKey\":\"" + str(tournament.key()) +"\", \"matchKey\":\"" + matchKey +"\" }
                
                if (isInTournamentJSON.isNull("error"))
                {                    
                    //then the player is in a tournament, ready to upload the match
                    initInTournamentVariables();//reset to null
                    //System.out.println( "player is in a tournament, ready to upload the match..." );
                    matchKey = isInTournamentJSON.getString("matchKey");
                    tournamentKey = isInTournamentJSON.getString("tournamentKey");
                    playerName = isInTournamentJSON.getString("playerName");
                    upload_url = isInTournamentJSON.getString("upload_url");
                    //System.out.println( playerName );                     
                    //showInfo(isInTournamentJSON.getString("matchKey"));
                     
                     return true;
                 }
                 else
                 {
                    String ErrorResponse = isInTournamentJSON.getString("error");
                    System.out.println( "ERROR: " + ErrorResponse );
                    return false;
                 }
                 
                 
             }
             catch ( JSONException je ) {
                  //TODO: any more exception handling?
                 je.printStackTrace();
             } 
             catch ( Exception e ) {
                  //TODO: any more exception handling?
                 e.printStackTrace();
             }
            
            finally {
                 //TODO: close/deallocate all necessary objs

             }            
             return false;
                                 
             
        
        }        
        private void showInfo(final String theOutputText)
        {
            //used for visual display during testing/dev
            final GuiUtilsApi guiUtils = generalServices.getGuiUtilsApi();
            final JDialog dialog = new JDialog(guiUtils.getMainFrame(), "test dialog" );
            dialog.setSize( 900, 600 );
            guiUtils.centerWindowToWindow( dialog, guiUtils.getMainFrame() );
            
            
            final JTextField jtf = new JTextField();
            final JTextArea jtextArea = new JTextArea();
            
            final JPanel textPanel = new JPanel();
            textPanel.add(jtf);
            dialog.add( textPanel, BorderLayout.NORTH);
            JLabel jlbHelloWorld = new JLabel(theOutputText);
            dialog.add( jlbHelloWorld, BorderLayout.NORTH);
            
            
            final JPanel buttonsPanel = new JPanel();            
            buttonsPanel.add( generalServices.getGuiUtilsApi().createCloseButton( dialog ) );
            buttonsPanel.add( jtextArea );
            dialog.add( buttonsPanel, BorderLayout.SOUTH );            
            
            dialog.setVisible( true );            
        }        
        
	@Override
        public void destroy() {
		// Remove our diagnostic test factory
		generalServices.getCallbackApi().removeDiagnosticTestFactory( diagnosticTestFactory );
		// Remove our new replay listener
		generalServices.getCallbackApi().removeNewReplayListener( newReplayListener );
	}	
	@Override
	public boolean isActionRequired() {
		// Action is NOT required if the user has his SC2BTCOpen user key configured (string)
		return isStringSettingMissing( KEY_SBO_PLAYERID );
			
	}	
	/**
	 * Checks if the string setting specified by its key is missing.
	 * A string setting is missing if its value is null or its length is zero.
	 * @param key key of the setting to check
	 * @return true if the setting specified by its key is missing; false otherwise
	 */
	private boolean isStringSettingMissing( final String key ) {
		final String value = pluginServices.getSettingsApi().getString( key );
		return value == null || value.length() == 0;
	}	
	@Override
	public SettingsControl getSettingsControl() {
		return new BaseSettingsControl() {
			/** Store the settings API reference. */
			private final SettingsApi settings = pluginServices.getSettingsApi();
			/** Store the language API reference. */
			//language API is available to plugins, but no support for the labguage XML file key/value pairs yet
                        //for everything is hardcoded into the plugin
                        //private final LanguageApi language = generalServices.getLanguageApi();
			
			/** Text field to view/edit SBO PlayerId.        */
                        private final JTextField SBOPlayerIdTextField   = new JTextField( settings.getString( KEY_SBO_PLAYERID    ) );
                        //System.out.println( "player is in a tournament, ready to upload the match..." );
                        
			@Override
			public Container getEditorPanel() {
                            
                                System.out.println(settings.getString( KEY_SBO_PLAYERID) );
				final JPanel panel = new JPanel( new BorderLayout() );
				
				final JPanel infoPanel = new JPanel( new GridLayout( 2, 1, 0, 3 ) );
				infoPanel.setBorder( BorderFactory.createEmptyBorder( 0, 0, 10, 0 ) );
                                infoPanel.add( new JLabel( "Please enter your Starcraft Bitcoin Open playerId (can be found @ http://sc2btcopen.appspot.com/account)" ) );
                                
				panel.add( infoPanel, BorderLayout.NORTH );
				
				final Box box = Box.createVerticalBox();
				
				Box row = Box.createHorizontalBox();				
                                row.add( new JLabel( "Player Id" ) );
				SBOPlayerIdTextField.setToolTipText( "Your numeric Starcraft Bitcoin Open Player Id." );
				row.add( SBOPlayerIdTextField );
                                //TODO: player validation/test whether the key is working... needs a handler server-side...                                
                                //row.add( createValidateButton  ( SBOPlayerIdTextField ) );
				//row.add( new JLabel() );
				//row.add( new JLabel() );
				box.add( row );

                                //only for use with multiple same-size controls
				//generalServices.getGuiUtilsApi().alignBox( box, 4 );
				
				panel.add( box, BorderLayout.CENTER );
				
				return panel;
			}
			
			/**
			 * Creates the validation button for validation against  SC2BTC webservice
			 * @param textField text field to validate
			 * @return a validate button associated with the specified text field
			 */
			private JButton createValidateButton( final JTextField textField ) {
				//if ( ICON_CONTROL == null )
				//	ICON_CONTROL = new ImageIcon( SBOAutomatonPlugin.class.getResource( "control.png" ) );
				
				//final JButton button = new JButton( "Validate", ICON_CONTROL );
                                final JButton button = new JButton( "Validate" );
				button.addActionListener( new ActionListener() {
					@Override
					public void actionPerformed( ActionEvent event ) {
						try {
							//generalServices.getSoundsApi().playSound( new FileInputStream( textField.getText() ), false );
                                                        //run the validation converstion
                                                        System.out.println("validation button pressed with: " + SBOPlayerIdTextField  .getText());
                                                    
						} catch ( Exception e ) {
                                                    e.printStackTrace();
						}
					}
				} );
				return button;
			}
			
			@Override
			public void onOkButtonPressed() {
				settings.set( KEY_SBO_PLAYERID   , SBOPlayerIdTextField  .getText().trim() );
			}
		};
	}
	
}
