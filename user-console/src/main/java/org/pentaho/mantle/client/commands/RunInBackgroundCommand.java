/*!
 *
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 *
 * Copyright (c) 2002-2021 Hitachi Vantara. All rights reserved.
 *
 */

package org.pentaho.mantle.client.commands;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNull;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;

import com.google.gwt.json.client.JSONValue;
import org.pentaho.gwt.widgets.client.dialogs.MessageDialogBox;
import org.pentaho.gwt.widgets.client.filechooser.RepositoryFile;
import org.pentaho.gwt.widgets.client.utils.NameUtils;
import org.pentaho.gwt.widgets.client.utils.string.StringUtils;
import org.pentaho.mantle.client.MantleUtils;
import org.pentaho.mantle.client.events.SolutionFileHandler;
import org.pentaho.mantle.client.messages.Messages;
import org.pentaho.mantle.client.solutionbrowser.SolutionBrowserPanel;
import org.pentaho.mantle.client.solutionbrowser.filelist.FileItem;
import org.pentaho.mantle.client.ui.PerspectiveManager;

import java.util.Date;

import static org.pentaho.mantle.client.environment.EnvironmentHelper.getFullyQualifiedURL;

/**
 * Run In Background Command
 *
 * Note that the run in background command functionality is similar to schedule functionality. While a lot of code is
 * duplicated over the two commands, quite a bit of screen flow / controller logic is embedded in the view dialogs.
 * Combining these would make more sense once the control flow is removed from the views and abstracted into controllers.
 */
public class RunInBackgroundCommand extends AbstractCommand {
  String moduleBaseURL = GWT.getModuleBaseURL();

  String moduleName = GWT.getModuleName();

  String contextURL = moduleBaseURL.substring( 0, moduleBaseURL.lastIndexOf( moduleName ) );

  private FileItem repositoryFile;

  public RunInBackgroundCommand() {
    setupNativeHooks( this );
  }

  public RunInBackgroundCommand( FileItem fileItem ) {
    this.repositoryFile = fileItem;
  }

  private String solutionPath = null;
  private String solutionTitle = null;
  private String outputLocationPath = null;
  private String outputName = null;
  private String overwriteFile;
  private String dateFormat;

  public String getSolutionTitle() {
    return solutionTitle;
  }

  public void setSolutionTitle( String solutionTitle ) {
    this.solutionTitle = solutionTitle;
  }

  public String getSolutionPath() {
    return solutionPath;
  }

  public void setSolutionPath( String solutionPath ) {
    this.solutionPath = solutionPath;
  }

  public String getOutputLocationPath() {
    return outputLocationPath;
  }

  public void setOutputLocationPath( String outputLocationPath ) {
    this.outputLocationPath = outputLocationPath;
  }

  public String getModuleBaseURL() {
    return moduleBaseURL;
  }

  public void setModuleBaseURL( String moduleBaseURL ) {
    this.moduleBaseURL = moduleBaseURL;
  }

  public String getOutputName() {
    return outputName;
  }

  public void setOutputName( String outputName ) {
    this.outputName = outputName;
  }

  /**
   * Get Date Format
   *
   * @return a string representation of a date format
   */
  public String getDateFormat() {
    return dateFormat;
  }

  /**
   * Set Date Format
   *
   * @param dateFormat a string representation of a date format
   */
  public void setDateFormat( String dateFormat ) {
    this.dateFormat = dateFormat;
  }

  /**
   * Get Overwrite File
   *
   * @return the string "true" if the file should be overwritten, otherwise "false"
   */
  public String getOverwriteFile() {
    return overwriteFile;
  }

  /**
   * Set Overwrite File
   *
   * @param overwriteFile the string "true" if the file should be overwritten, otherwise "false"
   */
  public void setOverwriteFile( String overwriteFile ) {
    this.overwriteFile = overwriteFile;
  }

  protected void performOperation() {
    final SolutionBrowserPanel sbp = SolutionBrowserPanel.getInstance();
    if ( this.getSolutionPath() != null ) {
      sbp.getFile( this.getSolutionPath(), new SolutionFileHandler() {
        @Override
        public void handle( RepositoryFile repositoryFile ) {
          RunInBackgroundCommand.this.repositoryFile = new FileItem( repositoryFile, null, null, false, null );
          checkSchedulePermissionAndDialog();
        }
      } );
    } else {
      performOperation( true );
    }
  }

  @SuppressWarnings ( "deprecation" )
  protected JSONObject getJsonSimpleTrigger( int repeatCount, int interval, Date startDate, Date endDate ) {
    JSONObject trigger = new JSONObject();
    trigger.put( "repeatInterval", new JSONNumber( interval ) ); //$NON-NLS-1$
    trigger.put( "repeatCount", new JSONNumber( repeatCount ) ); //$NON-NLS-1$
    trigger
        .put(
            "startTime", startDate != null ? new JSONString( DateTimeFormat.getFormat( PredefinedFormat.ISO_8601 ).format( startDate ) ) : JSONNull.getInstance() ); //$NON-NLS-1$
    if ( endDate != null ) {
      endDate.setHours( 23 );
      endDate.setMinutes( 59 );
      endDate.setSeconds( 59 );
    }
    trigger
        .put(
            "endTime", endDate == null ? JSONNull.getInstance() : new JSONString( DateTimeFormat.getFormat( PredefinedFormat.ISO_8601 ).format( endDate ) ) ); //$NON-NLS-1$
    return trigger;
  }

  protected void showDialog( final boolean feedback ) {

    createScheduleOutputLocationDialog( getSolutionPath(), feedback );

    final String filePath = solutionPath;
    String urlPath = NameUtils.URLEncode( NameUtils.encodeRepositoryPath( filePath ) );

    RequestBuilder scheduleFileRequestBuilder = createParametersChecker( urlPath );
    final boolean isXAction = isXAction( urlPath );

    try {
      scheduleFileRequestBuilder.sendRequest( null, new RequestCallback() {
        public void onError( Request request, Throwable exception ) {
          MessageDialogBox dialogBox =
              new MessageDialogBox( Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
          dialogBox.center();
        }

        public void onResponseReceived( Request request, Response response ) {
          if ( response.getStatusCode() == Response.SC_OK ) {
            String responseMessage = response.getText();
            boolean hasParams = hasParameters( responseMessage, isXAction );
            if ( !hasParams ) {
              setOkButtonText();
            }
            centerScheduleOutputLocationDialog();
          } else {
            MessageDialogBox dialogBox =
                new MessageDialogBox(
                    Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), false, false, true ); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            dialogBox.center();
          }
        }
      } );
    } catch ( RequestException e ) {
      MessageDialogBox dialogBox =
          new MessageDialogBox( Messages.getString( "error" ), e.toString(), false, false, true ); //$NON-NLS-1$
      dialogBox.center();
    }
  }

  private boolean hasParameters( String responseMessage, boolean isXAction ) {
    if ( isXAction ) {
      int numOfInputs = StringUtils.countMatches( responseMessage, "<input" );
      int numOfHiddenInputs = StringUtils.countMatches( responseMessage, "type=\"hidden\"" );
      return numOfInputs - numOfHiddenInputs > 0 ? true : false;
    } else {
      return Boolean.parseBoolean( responseMessage );
    }
  }

  private boolean isXAction( String urlPath ) {
    if ( ( urlPath != null ) && ( urlPath.endsWith( "xaction" ) ) ) {
      return true;
    } else {
      return false;
    }
  }

  private RequestBuilder createParametersChecker( String urlPath ) {
    RequestBuilder scheduleFileRequestBuilder = null;
    if ( ( urlPath != null ) && ( urlPath.endsWith( "xaction" ) ) ) {
      scheduleFileRequestBuilder = new RequestBuilder( RequestBuilder.GET, contextURL + "api/repos/" + urlPath
          + "/parameterUi" );
    } else {
      scheduleFileRequestBuilder = new RequestBuilder( RequestBuilder.GET, contextURL + "api/repo/files/" + urlPath
          + "/parameterizable" );
    }
    scheduleFileRequestBuilder.setHeader( "accept", "text/plain" ); //$NON-NLS-1$ //$NON-NLS-2$
    scheduleFileRequestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
    return scheduleFileRequestBuilder;
  }

  protected  void checkSchedulePermissionAndDialog() {
    final String url = MantleUtils.getSchedulerPluginContextURL() + "api/scheduler/isScheduleAllowed?id=" + repositoryFile.getRepositoryFile().getId(); //$NON-NLS-1$
    RequestBuilder requestBuilder = new RequestBuilder( RequestBuilder.GET, url );
    requestBuilder.setHeader( "accept", "text/plain" );
    requestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
    final MessageDialogBox errorDialog =
      new MessageDialogBox(
        Messages.getString( "error" ), Messages.getString( "noSchedulePermission" ), false, false, true ); //$NON-NLS-1$ //$NON-NLS-2$
    try {
      requestBuilder.sendRequest( null, new RequestCallback() {

        public void onError( Request request, Throwable caught ) {
          errorDialog.center();
        }

        public void onResponseReceived( Request request, Response response ) {
          if ( "true".equalsIgnoreCase( response.getText() ) ) {
            showDialog( true );
          } else {
            errorDialog.center();
          }
        }
      } );
    } catch ( RequestException re ) {
      errorDialog.center();
    }
  }

  protected void performOperation( boolean feedback ) {

    final String filePath = ( this.getSolutionPath() != null ) ? this.getSolutionPath() : repositoryFile.getPath();
    String urlPath = NameUtils.URLEncode( NameUtils.encodeRepositoryPath( filePath ) );

    RequestBuilder scheduleFileRequestBuilder = createParametersChecker( urlPath );
    final boolean isXAction = isXAction( urlPath );

    try {
      scheduleFileRequestBuilder.sendRequest( null, new RequestCallback() {

        public void onError( Request request, Throwable exception ) {
          MessageDialogBox dialogBox =
              new MessageDialogBox( Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
          dialogBox.center();
        }

        public void onResponseReceived( Request request, Response response ) {
          if ( response.getStatusCode() == Response.SC_OK ) {
            final JSONObject scheduleRequest = new JSONObject();
            scheduleRequest.put( "inputFile", new JSONString( filePath ) ); //$NON-NLS-1$

            //Set date format to append to filename
            if ( StringUtils.isEmpty( getDateFormat() ) ) {
              scheduleRequest.put( "appendDateFormat", JSONNull.getInstance() ); //$NON-NLS-1$
            } else {
              scheduleRequest.put( "appendDateFormat", new JSONString( getDateFormat() ) ); //$NON-NLS-1$
            }

            //Set whether to overwrite the file
            if ( StringUtils.isEmpty( getOverwriteFile() ) ) {
              scheduleRequest.put( "overwriteFile", JSONNull.getInstance() ); //$NON-NLS-1$
              } else {
              scheduleRequest.put( "overwriteFile", new JSONString( getOverwriteFile() ) ); //$NON-NLS-1$
            }

            // Set job name
            if ( StringUtils.isEmpty( getOutputName() ) ) {
              scheduleRequest.put( "jobName", JSONNull.getInstance() ); //$NON-NLS-1$
            } else {
              scheduleRequest.put( "jobName", new JSONString( getOutputName() ) ); //$NON-NLS-1$
            }

            // Set output path location
            if ( StringUtils.isEmpty( getOutputLocationPath() ) ) {
              scheduleRequest.put( "outputFile", JSONNull.getInstance() ); //$NON-NLS-1$
            } else {
              scheduleRequest.put( "outputFile", new JSONString( getOutputLocationPath() ) ); //$NON-NLS-1$
            }

            // BISERVER-9321
            scheduleRequest.put( "runInBackground", JSONBoolean.getInstance( true ) );

            String responseMessage = response.getText();
            final boolean hasParams = hasParameters( responseMessage, isXAction );

            RequestBuilder emailValidRequest =
                new RequestBuilder( RequestBuilder.GET, contextURL + "api/emailconfig/isValid" ); //$NON-NLS-1$
            emailValidRequest.setHeader( "accept", "text/plain" ); //$NON-NLS-1$ //$NON-NLS-2$
            emailValidRequest.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
            try {
              emailValidRequest.sendRequest( null, new RequestCallback() {

                public void onError( Request request, Throwable exception ) {
                  MessageDialogBox dialogBox =
                      new MessageDialogBox( Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
                  dialogBox.center();
                }

                public void onResponseReceived( Request request, Response response ) {
                  if ( response.getStatusCode() == Response.SC_OK ) {
                    // final boolean isEmailConfValid = Boolean.parseBoolean(response.getText());
                    // force false for now, I have a feeling PM is going to want this, making it easy to turn back
                    // on
                    final boolean isEmailConfValid = false;
                    if ( hasParams ) {
                      boolean isSchedulesPerspectiveActive = !PerspectiveManager.getInstance().getActivePerspective().getId().equals(PerspectiveManager.SCHEDULES_PERSPECTIVE );
                      createScheduleParamsDialog( filePath, scheduleRequest, isEmailConfValid, isSchedulesPerspectiveActive );
                    } else if ( isEmailConfValid ) {
                      createScheduleEmailDialog( filePath, scheduleRequest );
                    } else {
                      // Handle Schedule Parameters
                      String jsonStringScheduleParams = getScheduleParams( scheduleRequest );
                      JSONValue scheduleParams = JSONParser.parseStrict( jsonStringScheduleParams );
                      scheduleRequest.put( "jobParameters", scheduleParams.isArray() );

                      // just run it
                      RequestBuilder scheduleFileRequestBuilder =
                          new RequestBuilder( RequestBuilder.POST, MantleUtils.getSchedulerPluginContextURL() + "api/scheduler/job" ); //$NON-NLS-1$
                      scheduleFileRequestBuilder.setHeader( "Content-Type", "application/json" ); //$NON-NLS-1$//$NON-NLS-2$
                      scheduleFileRequestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );

                      try {
                        scheduleFileRequestBuilder.sendRequest( scheduleRequest.toString(), new RequestCallback() {

                          @Override
                          public void onError( Request request, Throwable exception ) {
                            MessageDialogBox dialogBox =
                                new MessageDialogBox(
                                    Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
                            dialogBox.center();
                          }

                          @Override
                          public void onResponseReceived( Request request, Response response ) {
                            if ( response.getStatusCode() == 200 ) {
                              MessageDialogBox dialogBox =
                                  new MessageDialogBox(
                                      Messages.getString( "runInBackground" ), Messages.getString( "backgroundExecutionStarted" ), //$NON-NLS-1$ //$NON-NLS-2$
                                      false, false, true );
                              dialogBox.center();
                            } else {
                              MessageDialogBox dialogBox =
                                  new MessageDialogBox(
                                      Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-2$ //$NON-NLS-3$
                                      false, false, true );
                              dialogBox.center();
                            }
                          }

                        } );
                      } catch ( RequestException e ) {
                        MessageDialogBox dialogBox = new MessageDialogBox( Messages.getString( "error" ), e.toString(), //$NON-NLS-1$
                            false, false, true );
                        dialogBox.center();
                      }
                    }

                  }
                }
              } );
            } catch ( RequestException e ) {
              MessageDialogBox dialogBox =
                  new MessageDialogBox( Messages.getString( "error" ), e.toString(), false, false, true ); //$NON-NLS-1$
              dialogBox.center();
            }

          } else {
            MessageDialogBox dialogBox =
                new MessageDialogBox(
                    Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), false, false, true ); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            dialogBox.center();
          }
        }

      } );
    } catch ( RequestException e ) {
      MessageDialogBox dialogBox =
          new MessageDialogBox( Messages.getString( "error" ), e.toString(), false, false, true ); //$NON-NLS-1$
      dialogBox.center();
    }
  }

  private static native void setupNativeHooks( RunInBackgroundCommand cmd )
  /*-{
    $wnd.mantle_runInBackgroundCommand_setOutputName = function(outputName) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setOutputName(Ljava/lang/String;)(outputName);
    }

    $wnd.mantle_runInBackgroundCommand_setOutputLocationPath = function(outputLocationPath) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setOutputLocationPath(Ljava/lang/String;)(outputLocationPath);
    }

    $wnd.mantle_runInBackgroundCommand_setOverwriteFile = function(overwriteFile) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setOverwriteFile(Ljava/lang/String;)(overwriteFile);
    }

    $wnd.mantle_runInBackgroundCommand_setDateFormat = function(dateFormat) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setDateFormat(Ljava/lang/String;)(dateFormat);
    }

    $wnd.mantle_runInBackgroundCommand_performOperation = function(feedback) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::performOperation(Z)(feedback);
    }
  }-*/;

  private native void createScheduleOutputLocationDialog(String solutionPath, Boolean feedback) /*-{
   $wnd.pho.createScheduleOutputLocationDialog(solutionPath, feedback);
  }-*/;

  private native void setOkButtonText() /*-{
   $wnd.pho.setOkButtonText();
  }-*/;

  private native void centerScheduleOutputLocationDialog() /*-{
   $wnd.pho.centerScheduleOutputLocationDialog();
  }-*/;

  private native void createScheduleParamsDialog( String filePath, JSONObject scheduleRequest, Boolean isEmailConfigValid, Boolean isSchedulesPerspectiveActive ) /*-{
   $wnd.pho.createScheduleParamsDialog( filePath, scheduleRequest, isEmailConfigValid, isSchedulesPerspectiveActive );
  }-*/;

  private native void createScheduleEmailDialog( String filePath, JSONObject scheduleRequest ) /*-{
   $wnd.pho.createScheduleEmailDialog( filePath, scheduleRequest );
  }-*/;

  private native String getScheduleParams( JSONObject scheduleRequest) /*-{
   return $wnd.pho.getScheduleParams( scheduleRequest );
  }-*/;
}
