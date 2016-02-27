/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teeptrak.controller;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;

import com.teeptrak.controller.dfu.DfuService;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;


public class MainActivity extends Activity implements LoaderCallbacks<Cursor>
{
  public  static final String  TAG                        = "TeepTrakController";
  private static final int     REQUEST_SELECT_DEVICE      = 1;
  private static final int     REQUEST_ENABLE_BT          = 2;
  private static final int     REQUEST_SELECT_FILE        = 3;

  private static final int     TTC_BUSY                   = 10;
  private static final int     TTC_READY                  = 11;
  private static final int     UART_PROFILE_CONNECTED     = 20;
  private static final int     UART_PROFILE_DISCONNECTED  = 21;

  private static final int     FILE_TYPE_NONE             = -1;
  private static final int     FILE_TYPE_ZIP              = 1;
  private static final int     FILE_TYPE_CFG              = 2;
  private static final int     FILE_TYPE_TST              = 3;

  private static final int     STATE_OFF                  = 10;

  private static final String  MIME_TYPE_TEXT             = "text/plain";
  //private static final String  MIME_TYPE_CFG              = "text/plain";
  //private static final String  MIME_TYPE_TST              = "text/plain";

  private static final String  EXTRA_URI                  = "uri";

  private TextView             mBtDeviceAddress;
  private int                  mState                     = UART_PROFILE_DISCONNECTED;
  private UartService          mUartService               = null;
  private BluetoothDevice      mBtDevice                  = null;
  private BluetoothAdapter     mBtAdapter                 = null;
  private ListView             mMsgList;
  private ArrayAdapter<String> mMsgListAdapter;
  private Button               mConnectBtn;
  private Button               mSendBtn;
  private Button               mDatBtn;
  private Button               mUpgradeBtn;
  private Button               mSetFwFileBtn;
  private Button               mSetCfgFileBtn;
  private Button               mSetTstFileBtn;
  private Button               mConfigDeviceBtn;
  private Button               mTestDeviceBtn;
  private EditText             mSendMsg;

  private TextView             mStateLabel;
  private TextView             mBtDeviceName;
  private ProgressBar          mProgressBar;
  private CheckBox             mLedBox;

  private Uri                  mFileUri                   = null;
  private int                  mFileType                  = FILE_TYPE_NONE;
  private String               mFwFilePath                = null;
  private String               mCfgFilePath               = null;
  private String               mTstFilePath               = null;

  private aScriptTask          mScriptTask                = null;

  
  
  private final DfuProgressListener mProgressBarListener = new DfuProgressListenerAdapter()
  {
    @Override
    public void onDeviceConnecting(final String deviceAddress)
    {
      mProgressBar.setIndeterminate(true);
      mStateLabel.setText(R.string.dfu_status_connecting);
      printMessage(getString(R.string.dfu_status_connecting), true);
    }

    @Override
    public void onDfuProcessStarting(final String deviceAddress)
    {
      mProgressBar.setIndeterminate(true);
      mStateLabel.setText(R.string.dfu_status_starting);
      printMessage(getString(R.string.dfu_status_starting), true);
    }

    @Override
    public void onEnablingDfuMode(final String deviceAddress)
    {
      mProgressBar.setIndeterminate(true);
      mStateLabel.setText(R.string.dfu_status_switching_to_dfu);
      printMessage(getString(R.string.dfu_status_switching_to_dfu), true);
    }

    @Override
    public void onFirmwareValidating(final String deviceAddress)
    {
      mProgressBar.setIndeterminate(true);
      mStateLabel.setText(R.string.dfu_status_validating);
      printMessage(getString(R.string.dfu_status_validating), true);
    }

    @Override
    public void onDeviceDisconnecting(final String deviceAddress)
    {
      mProgressBar.setIndeterminate(true);
      mStateLabel.setText(R.string.dfu_status_disconnecting);
      printMessage(getString(R.string.dfu_status_disconnecting), true);
    }

    @Override
    public void onDfuCompleted(final String deviceAddress)
    {
      mProgressBar.setIndeterminate(false);
      mProgressBar.setProgress(0);
      mStateLabel.setText(R.string.dfu_status_completed);

      // Let's wait a bit until we cancel the notification.
      // When canceled immediately it will be recreated by service again.
      new Handler().postDelayed(new Runnable()
      {
        @Override
        public void run()
        {
          //onTransferCompleted();
          //clearUI(true);
          //showToast(R.string.dfu_success);
          printMessage(getString(R.string.dfu_status_completed), true);
          // If this activity is still open and upload process was completed, cancel the notification
          final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
          manager.cancel(DfuService.NOTIFICATION_ID);
        }
      }, 200);
    }

    @Override
    public void onDfuAborted(final String deviceAddress)
    {
      mStateLabel.setText(R.string.dfu_status_aborted);
      // Let's wait a bit until we cancel the notification.
      // When canceled immediately it will be recreated by service again.
      new Handler().postDelayed(new Runnable()
      {
        @Override
        public void run()
        {
          //onUploadCanceled();
          //clearUI(false);
          //showToast(R.string.dfu_aborted);
          printMessage(getString(R.string.dfu_status_aborted), true);
          // if this activity is still open and upload process was completed, cancel the notification
          final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
          manager.cancel(DfuService.NOTIFICATION_ID);
        }
      }, 200);
    }

    @Override
    public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal)
    {
      mProgressBar.setIndeterminate(false);
      mProgressBar.setProgress(percent);
      mStateLabel.setText(getString(R.string.dfu_uploading_percentage, percent));

      //if (partsTotal > 1)
      //  mTextUploading.setText(getString(R.string.dfu_status_uploading_part, currentPart, partsTotal));
      //else
      //  mTextUploading.setText(R.string.dfu_status_uploading);
    }

    @Override
    public void onError(final String deviceAddress, final int error, final int errorType, final String message)
    {
      //showErrorMessage(message);
      mStateLabel.setText(getString(R.string.dfu_uploading_error, 0));
      // We have to wait a bit before canceling notification. This is called before DfuService creates the last notification.
      new Handler().postDelayed(new Runnable()
      {
        @Override
        public void run()
        {
          printMessage(getString(R.string.dfu_uploading_error, 0), true);
          // if this activity is still open and upload process was completed, cancel the notification
          final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
          manager.cancel(DfuService.NOTIFICATION_ID);
        }
      }, 200);
    }
  };


  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    mBtAdapter = BluetoothAdapter.getDefaultAdapter();

    if(mBtAdapter == null)
    {
      Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
      finish();
      return;
    }

    mConnectBtn       = (Button) findViewById(R.id.idConnectBtn);
    mBtDeviceName     = (TextView) findViewById(R.id.idDeviceName);
    mBtDeviceAddress  = (TextView) findViewById(R.id.idDeviceAddress);
    mStateLabel       = (TextView) findViewById(R.id.idStateLabel);
    mProgressBar      = (ProgressBar) findViewById(R.id.idProgressBar);
    mMsgList          = (ListView) findViewById(R.id.idMessageList);
    mSendMsg          = (EditText) findViewById(R.id.idSendText);
    mLedBox           = (CheckBox) findViewById(R.id.idLedBox);
    mDatBtn           = (Button) findViewById(R.id.idDatBtn);
    mSendBtn          = (Button) findViewById(R.id.idSendBtn);
    mSetFwFileBtn     = (Button) findViewById(R.id.idSetFwFileBtn);
    mUpgradeBtn       = (Button) findViewById(R.id.idUpgradeFwBtn);
    mSetCfgFileBtn    = (Button) findViewById(R.id.idSetCfgFileBtn);
    mConfigDeviceBtn  = (Button) findViewById(R.id.idCinfigDeviceBtn);
    mSetTstFileBtn    = (Button) findViewById(R.id.idSetTstFileBtn);
    mTestDeviceBtn    = (Button) findViewById(R.id.idTestDeviceBtn);

    mMsgListAdapter   = new ArrayAdapter<String>(this, R.layout.message_detail);
    mMsgList.setAdapter(mMsgListAdapter);
    mMsgList.setDivider(null);

    initUartService();
  }

  private void printMessage(String aMsg, boolean aInsertDate)
  {
    String dateTime = DateFormat.getTimeInstance().format(new Date());
    if (aInsertDate)
    {
      mMsgListAdapter.add("[" + dateTime + "] " + aMsg);
    }
    else
    {
      mMsgListAdapter.add("           " + aMsg);
    }
    mMsgList.smoothScrollToPosition(mMsgListAdapter.getCount() - 1);
  }


  //UART service connected/disconnected
  private ServiceConnection mUartServiceConnection = new ServiceConnection()
  {
    public void onServiceConnected(ComponentName className, IBinder rawBinder)
    {
      mUartService = ((UartService.LocalBinder) rawBinder).getService();
      Log.d(TAG, "onServiceConnected mUartService= " + mUartService);
      if (!mUartService.initialize())
      {
        Log.e(TAG, "Unable to initialize Bluetooth");
        finish();
      }
    }

    public void onServiceDisconnected(ComponentName classname)
    {
      ////mUartService.disconnect(mBtDevice);
      mUartService = null;
    }
  };

  private Handler mHandler = new Handler()
  {
        @Override
        //Handler events that received from UART service
        public void handleMessage(Message msg)
        {

        }
  };

  private void setUIState(int aState)
  {
    switch(aState)
    {
      case UART_PROFILE_CONNECTED:
        mConnectBtn.setText("Disconnect");
        mConnectBtn.setEnabled(true);
        mStateLabel.setText("Connected");
        mSendMsg.setEnabled(true);
        mSendBtn.setEnabled(true);
        mDatBtn.setEnabled(true);
        mSetFwFileBtn.setEnabled(true);
        mUpgradeBtn.setEnabled(true);
        mSetCfgFileBtn.setEnabled(true);
        mConfigDeviceBtn.setEnabled(true);
        mSetTstFileBtn.setEnabled(true);
        mTestDeviceBtn.setEnabled(true);
        mLedBox.setEnabled(true);
        break;
      case UART_PROFILE_DISCONNECTED:
        mConnectBtn.setText("Connect");
        mConnectBtn.setEnabled(true);
        mStateLabel.setText("Disconnected");
        mSendMsg.setEnabled(false);
        mSendBtn.setEnabled(false);
        mDatBtn.setEnabled(false);
        mSetFwFileBtn.setEnabled(true);
        mUpgradeBtn.setEnabled(false);
        mSetCfgFileBtn.setEnabled(true);
        mConfigDeviceBtn.setEnabled(false);
        mSetTstFileBtn.setEnabled(true);
        mTestDeviceBtn.setEnabled(false);
        mLedBox.setEnabled(false);
        break;
      case TTC_BUSY:
        mConnectBtn.setText("Disconnect");
        mConnectBtn.setEnabled(false);
        mStateLabel.setText("Connected -> Busy");
        mSendMsg.setEnabled(false);
        mSendBtn.setEnabled(false);
        mDatBtn.setEnabled(false);
        mSetFwFileBtn.setEnabled(false);
        mUpgradeBtn.setEnabled(false);
        mSetCfgFileBtn.setEnabled(false);
        mConfigDeviceBtn.setEnabled(false);
        mSetTstFileBtn.setEnabled(false);
        mTestDeviceBtn.setEnabled(false);
        mLedBox.setEnabled(false);
        break;
      case TTC_READY:
        mConnectBtn.setText("Disconnect");
        mConnectBtn.setEnabled(true);
        mStateLabel.setText("Connected -> Ready");
        mSendMsg.setEnabled(true);
        mSendBtn.setEnabled(true);
        mDatBtn.setEnabled(true);
        mSetFwFileBtn.setEnabled(true);
        mUpgradeBtn.setEnabled(true);
        mSetCfgFileBtn.setEnabled(true);
        mConfigDeviceBtn.setEnabled(true);
        mSetTstFileBtn.setEnabled(true);
        mTestDeviceBtn.setEnabled(true);
        mLedBox.setEnabled(true);
        break;
    }
  }

  private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction();

      final Intent mIntent = intent;

      //*********************//
      if (action.equals(UartService.ACTION_GATT_CONNECTED))
      {
        runOnUiThread(new Runnable()
        {
          public void run()
          {
            Log.d(TAG, "UART_CONNECT_MSG");

            mBtDeviceName.setText(mBtDevice.getName() + " - Ready");
            mBtDeviceAddress.setText(mBtDevice.getAddress());

            printMessage("Connected to: " + mBtDevice.getName(), true);

            mState = UART_PROFILE_CONNECTED;

            setUIState(mState);
          }
        });
      }

      //*********************//
      if(action.equals(UartService.ACTION_GATT_DISCONNECTED))
      {
        runOnUiThread(new Runnable()
        {
          public void run()
          {
            Log.d(TAG, "UART_DISCONNECT_MSG");

            mBtDeviceName.setText("Not Connected");
            mBtDeviceAddress.setText("-");

            printMessage("Disconnected to: " + mBtDevice.getName(), true);

            mUartService.close();

            mState = UART_PROFILE_DISCONNECTED;

            setUIState(mState);
          }
        });
      }


      //*********************//
      if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED))
      {
        mUartService.enableTXNotification();
      }


      //*********************//
      if (action.equals(UartService.ACTION_DATA_AVAILABLE))
      {
        final byte[] mRxValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);

        if (mScriptTask != null)
        {
          mScriptTask.putRxData(mRxValue);

          synchronized(mScriptTask)
          {
            mScriptTask.notify();
          }
        }
        else
        {
          runOnUiThread(new Runnable()
          {
            public void run()
            {
              try
              {
                String mText = new String(mRxValue, "UTF-8");
                printMessage("RX: " + mText, true);
              } catch(Exception e)
              {
                Log.e(TAG, e.toString());
              }
            }
          });
        }
      }


      //*********************//
      if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART))
      {
       	showMessage("Device doesn't support UART. Disconnecting");
      	mUartService.disconnect();
      }

    }
  };

  private void initUartService()
  {
    Intent bindIntent = new Intent(this, UartService.class);
    bindService(bindIntent, mUartServiceConnection, Context.BIND_AUTO_CREATE);

    LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
  }


  private static IntentFilter makeGattUpdateIntentFilter()
  {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
    intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
    intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
    intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
    return intentFilter;
  }


  @Override
  public void onStart()
  {
    super.onStart();
  }

  @Override
  public void onDestroy()
  {
  	 super.onDestroy();

     Log.d(TAG, "onDestroy()");

     try
     {
      	LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
     }
     catch (Exception ignore)
     {
        Log.e(TAG, ignore.toString());
     }

     unbindService(mUartServiceConnection);
     mUartService.stopSelf();
     mUartService= null;
  }


  @Override
  protected void onStop()
  {
    Log.d(TAG, "onStop");
    super.onStop();
  }


  @Override
  protected void onPause()
  {
    Log.d(TAG, "onPause");
    super.onPause();
    DfuServiceListenerHelper.unregisterProgressListener(this, mProgressBarListener);
  }


  @Override
  protected void onRestart()
  {
    super.onRestart();
    Log.d(TAG, "onRestart");
  }


  @Override
  public void onResume()
  {
    super.onResume();

    Log.d(TAG, "onResume");

    DfuServiceListenerHelper.registerProgressListener(this, mProgressBarListener);

    if (!mBtAdapter.isEnabled())
    {
      Log.i(TAG, "onResume - BT not enabled yet");
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }
  }


  @Override
  public void onConfigurationChanged(Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    switch (requestCode)
    {
      case REQUEST_SELECT_DEVICE:
      {
        //When the DeviceListActivity return, with the selected device address
        if(resultCode == Activity.RESULT_OK && data != null)
        {
          String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
          mBtDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

          Log.d(TAG, "... onActivityResultdevice.address==" + mBtDevice + "mserviceValue" + mUartService);

          mBtDeviceName.setText(mBtDevice.getName() + " - Connecting");

          if(mBtDevice.getName().equals("DfuTarg"))
          {
            if(mFwFilePath != null)
            {
              final DfuServiceInitiator DFU = new DfuServiceInitiator(mBtDevice.getAddress())
                      .setDeviceName(mBtDevice.getName())
                      .setKeepBond(false) //keepBond);
                              //if (mFileType == DfuService.TYPE_AUTO)

                      .setZip(null, mFwFilePath);

              //else {
              //  starter.setBinOrHex(mFileType, mFileStreamUri, mFilePath).setInitFile(mInitFileStreamUri, mInitFilePath);
              //}
              DFU.start(this, DfuService.class);
            } else
            {
              printMessage("Selected Device is in DFU mode.", false);
              printMessage("You must Set FirmWare file first!", false);
              Toast.makeText(this, "You must Set FirmWare file first!", Toast.LENGTH_SHORT).show();
            }
          } else
          {
            mUartService.connect(deviceAddress);
          }
        }
        break;
      }

      case REQUEST_ENABLE_BT:
      {
        // When the request to enable Bluetooth returns
        if(resultCode == Activity.RESULT_OK)
        {
          Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
        } else
        {
          // User did not enable Bluetooth or an error occurred
          Log.d(TAG, "BT not enabled");
          Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
          finish();
        }
        break;
      }

      case REQUEST_SELECT_FILE:
      {
        // Clear previous data
        mFwFilePath = null;
        mCfgFilePath = null;
        mTstFilePath = null;
        mFileUri = null;

        // Read new one
        final Uri uri = data.getData();
			  /*
			   * The URI returned from application may be in 'file' or 'content' schema.
			   * 'File' schema allows us to create a File object and read details from it
			   * directly. Data from 'Content' schema must be read by Content Provider.
			   * To do that we are using a Loader.
			   */
        if(uri.getScheme().equals("file"))
        {
          // the direct path to the file has been returned
          final String path = uri.getPath();
          final File file = new File(path);

          switch(mFileType)
          {
            case FILE_TYPE_ZIP:
              mFwFilePath = path;
              break;
            case FILE_TYPE_CFG:
              mCfgFilePath = path;
              break;
            case FILE_TYPE_TST:
              mTstFilePath = path;
              break;
            case FILE_TYPE_NONE:
            default:
              break;
          }

          updateFileInfo(file.getName(), file.length(), path, mFileType);
        }
        else if(uri.getScheme().equals("content"))
        {
          // an Uri has been returned
          mFileUri = uri;
          // If application returned Uri for streaming, let's use it. Does it works?
          // FIXME both Uris works with Google Drive app. Why both? What's the difference?
          // How about other apps like DropBox?
          final Bundle extras = data.getExtras();
          if(extras != null && extras.containsKey(Intent.EXTRA_STREAM))
              mFileUri = extras.getParcelable(Intent.EXTRA_STREAM);

          // File name and size must be obtained from Content Provider
          final Bundle bundle = new Bundle();
          bundle.putParcelable(EXTRA_URI, uri);
          getLoaderManager().restartLoader(REQUEST_SELECT_FILE, bundle, this);
        }
        break;
      }

      default:
        Log.e(TAG, "wrong request code");
        break;
    }
  }


  private void showMessage(String msg)
  {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
  }


  @Override
  public void onBackPressed()
  {
    if (mState == UART_PROFILE_CONNECTED)
    {
      Intent startMain = new Intent(Intent.ACTION_MAIN);
      startMain.addCategory(Intent.CATEGORY_HOME);
      startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startMain);
      showMessage("nRFUART's running in background.\n             Disconnect to exit");
    }
    else
    {
      new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.popup_title)
            .setMessage(R.string.popup_message)
            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
      {
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
   	      finish();
        }
      })
            .setNegativeButton(R.string.popup_no, null)
            .show();
    }
  }



  /************************************************************************************************/
  /*** Buttons Click Callbacks ********************************************************************/
  /************************************************************************************************/

  // Handler Disconnect & Connect button
  public void onConnectBtnClicked(final View v)
  {
    if(!mBtAdapter.isEnabled())
    {
      Log.i(TAG, "onClick - BT not enabled yet");
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }
    else
    {
      if(mConnectBtn.getText().equals("Connect"))
      {
        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
      }
      else
      {
        //Disconnect button pressed
        if(mBtDevice != null)
        {
          mUartService.disconnect();
        }
      }
    }
  }

  // Handler Send button
  public void onSendBtnClicked(final View v)
  {
    String message = mSendMsg.getText().toString();

    byte[] value;

    try
    {
      //Send Data to Service
      value = message.getBytes("UTF-8");
      mUartService.writeRXCharacteristic(value);

      //Update the log with time stamp
      String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
      mMsgListAdapter.add("["+currentDateTimeString+"] TX: "+ message);
      mMsgList.smoothScrollToPosition(mMsgListAdapter.getCount() - 1);

      mSendMsg.setText("");
    }
    catch (UnsupportedEncodingException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }


  // Handler DAT Button
  public void onDatBtnClicked(final View v)
  {
    String message = "DAT";

    byte[] value;

    try
    {
      //Send data to service
      value = message.getBytes("UTF-8");
      mUartService.writeRXCharacteristic(value);

      //Update the log with time stamp
      String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
      mMsgListAdapter.add("["+currentDateTimeString+"] TX: "+ message);
      mMsgList.smoothScrollToPosition(mMsgListAdapter.getCount() - 1);

      mSendMsg.setText("");
    }
    catch (UnsupportedEncodingException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }


  // Handler Upload Button
  public void onUpgradeBtnClicked(final View view)
  {
    if (isDfuServiceRunning())
    {
      showUploadCancelDialog();
      return;
    }

    if (mFwFilePath == null)
    {
      printMessage("ERROR: Wrong FirmWare File!", true);
      return;
    }

      // Check whether the selected file is a HEX file (we are just checking the extension)
      //if (!mStatusOk)
      //{
      //  Toast.makeText(this, R.string.dfu_file_status_invalid_message, Toast.LENGTH_LONG).show();
      //  return;
      //}

      // Save current state in order to restore it if user quit the Activity
      //final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
      //final SharedPreferences.Editor editor = preferences.edit();
      //editor.putString(PREFS_DEVICE_NAME, mSelectedDevice.getName());
      //editor.putString(PREFS_FILE_NAME, mFileNameView.getText().toString());
      //editor.putString(PREFS_FILE_TYPE, mFileTypeView.getText().toString());
      //editor.putString(PREFS_FILE_SIZE, mFileSizeView.getText().toString());
      //editor.apply();

      //showProgressBar();

      //final boolean keepBond = preferences.getBoolean(SettingsFragment.SETTINGS_KEEP_BOND, false);

    final DfuServiceInitiator starter = new DfuServiceInitiator(mBtDevice.getAddress())
            .setDeviceName(mBtDevice.getName())
            .setKeepBond(false) //keepBond);
    //if (mFileType == DfuService.TYPE_AUTO)
            .setZip(null, mFwFilePath);
    //else {
    //  starter.setBinOrHex(mFileType, mFileStreamUri, mFilePath).setInitFile(mInitFileStreamUri, mInitFilePath);
    //}
    starter.start(this, DfuService.class);
  }


  public void onLedBoxClicked(final View view)
  {
    String message;

    if(mLedBox.isChecked())
    {
      message = "N5";
    }
    else
    {
      message = "F5";
    }

    byte[] value;

    try
    {
      value = message.getBytes("UTF-8");
      mUartService.writeRXCharacteristic(value);

      printMessage("TX: "+ message, true);

      mSendMsg.setText("");
    }
    catch (UnsupportedEncodingException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }


  private void showUploadCancelDialog()
  {
    final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
    final Intent pauseAction = new Intent(DfuService.BROADCAST_ACTION);
    pauseAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_PAUSE);
    manager.sendBroadcast(pauseAction);

    //final UploadCancelFragment fragment = UploadCancelFragment.getInstance();
    //fragment.show(getSupportFragmentManager(), TAG);
  }


  private boolean isDfuServiceRunning()
  {
    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
    {
      if (DfuService.class.getName().equals(service.service.getClassName()))
      {
        return true;
      }
    }
    return false;
  }




  /************************************************************************************************/
  /* Loader Callbacks */
  /************************************************************************************************/

  @Override
  public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
  {
    final Uri uri = args.getParcelable(EXTRA_URI);
		/*
		 * Some apps, f.e. Google Drive allow to select file that is not on the device. There is no "_data" column handled by that provider. Let's try to obtain
		 * all columns and than check which columns are present.
		 */
    // final String[] projection = new String[] { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA };
    return new CursorLoader(this, uri, null /* all columns, instead of projection */, null, null, null);
  }

  @Override
  public void onLoaderReset(final Loader<Cursor> loader)
  {
    //mFileNameView.setText(null);
    //mFileTypeView.setText(null);
    //mFileSizeView.setText(null);
    mFwFilePath = null;
    mCfgFilePath = null;
    mTstFilePath = null;
    mFileUri = null;
    //mStatusOk = false;
  }

  @Override
  public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
  {
    if (data != null && data.moveToNext())
    {
			/*
			 * Here we have to check the column indexes by name as we have requested for all. The order may be different.
			 */
      final String fileName = data.getString(data.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)/* 0 DISPLAY_NAME */);
      final int fileSize = data.getInt(data.getColumnIndex(MediaStore.MediaColumns.SIZE) /* 1 SIZE */);
      String filePath = null;
      final int dataIndex = data.getColumnIndex(MediaStore.MediaColumns.DATA);
      if (dataIndex != -1)
      {
        filePath = data.getString(dataIndex /* 2 DATA */);
      }
      if (!TextUtils.isEmpty(filePath))
      {
        switch(mFileType)
        {
          case FILE_TYPE_ZIP:
            mFwFilePath = filePath;
            break;
          case FILE_TYPE_CFG:
            mCfgFilePath = filePath;
            break;
          case FILE_TYPE_TST:
            mTstFilePath = filePath;
            break;
          case FILE_TYPE_NONE:
          default:
            break;
        }
      }

      updateFileInfo(fileName, fileSize, filePath, mFileType);
    }
    else
    {
      //mFileNameView.setText(null);
      //mFileTypeView.setText(null);
      //mFileSizeView.setText(null);
      mFwFilePath = null;
      mCfgFilePath = null;
      mTstFilePath = null;
      mFileUri = null;
      //mFileStatusView.setText(R.string.dfu_file_status_error);
      //mStatusOk = false;
    }
  }


  private void updateFileInfo(String aFileName, long aFileSize, String aFilePath, int aFileType)
  {
    String mType;

    //mFileNameView.setText(fileName);
    switch(aFileType)
    {
      case FILE_TYPE_ZIP:
        mType = "ZIP";
        break;
      case FILE_TYPE_CFG:
        mType = "CFG";
        break;
      case FILE_TYPE_TST:
        mType = "TST";
        break;
      case FILE_TYPE_NONE:
      default:
        mType  = "NONE";
        break;
    }
    //mFileSizeView.setText(getString(R.string.dfu_file_size_text, fileSize));
    //printMessage("Set File Action :", true);

    printMessage("- Path = " + aFilePath, false);
    printMessage("- Name = " + aFileName, false);
    printMessage("- Size = " + aFileSize + " Bytes", false);
    printMessage("- Type = " + mType, false);

    //final String extension = mFileType == DfuService.TYPE_AUTO ? "(?i)ZIP" : "(?i)HEX|BIN"; // (?i) =  case insensitive
    //final boolean statusOk = mStatusOk = MimeTypeMap.getFileExtensionFromUrl(fileName).matches(extension);
    //mFileStatusView.setText(statusOk ? R.string.dfu_file_status_ok : R.string.dfu_file_status_invalid);
    //mUploadButton.setEnabled(mSelectedDevice != null && statusOk);

    // Ask the user for the Init packet file if HEX or BIN files are selected. In case of a ZIP file the Init packets should be included in the ZIP.
    //if (statusOk && fileType != DfuService.TYPE_AUTO) {
    //  new AlertDialog.Builder(this).setTitle(R.string.dfu_file_init_title).setMessage(R.string.dfu_file_init_message)
    //          .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
    //            @Override
    //            public void onClick(final DialogInterface dialog, final int which) {
    //              mInitFilePath = null;
    //              mInitFileStreamUri = null;
    //            }
    //          }).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
    //    @Override
    //    public void onClick(final DialogInterface dialog, final int which) {
    //      final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    //      intent.setType(DfuService.MIME_TYPE_OCTET_STREAM);
    //      intent.addCategory(Intent.CATEGORY_OPENABLE);
    //      startActivityForResult(intent, SELECT_INIT_FILE_REQ);
    //    }
    //  }).show();
    //}
  }



  /************************************************************************************************/
  /* Set File Functions */
  /************************************************************************************************/

  /**
   * Called when Select File was pressed
   *
   * @param view a button that was pressed
   */
  public void onSetFwFileBtnClicked(final View view)
  {
    printMessage("Set FirmWare File Action Performed", true);
    //printMessage("           Path = " + mFilePath, false);
    //mFileTypeTmp = mFileType;
    //int index = 0;
    //switch (mFileType) {
    //  case DfuService.TYPE_AUTO:
    //    index = 0;
    //    break;
    //  case DfuService.TYPE_SOFT_DEVICE:
    //    index = 1;
    //    break;
    //  case DfuService.TYPE_BOOTLOADER:
    //    index = 2;
    //    break;
    //  case DfuService.TYPE_APPLICATION:
    //    index = 3;
    //    break;
    //}
    // Show a dialog with file types
    //  new AlertDialog.Builder(this)
    //        .setTitle(R.string.dfu_title)
    //        .setSingleChoiceItems(R.array.dfu_file_type, index, new DialogInterface.OnClickListener() {
    //          @Override
    //          public void onClick(final DialogInterface dialog, final int which) {
    //            switch (which) {
    //              case 0:
    //                mFileTypeTmp = DfuService.TYPE_AUTO;
    //                break;
    //              case 1:
    //                mFileTypeTmp = DfuService.TYPE_SOFT_DEVICE;
    //                break;
    //              case 2:
    //                mFileTypeTmp = DfuService.TYPE_BOOTLOADER;
    //                break;
    //              case 3:
    //                mFileTypeTmp = DfuService.TYPE_APPLICATION;
    //                break;
    //            }
    //          }
    //        }).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    //  @Override
    //  public void onClick(final DialogInterface dialog, final int which) {
      mFileType = FILE_TYPE_ZIP;
      //openFileChooser(mFileType);
    //  }
    //}).setNeutralButton(R.string.dfu_file_info, new DialogInterface.OnClickListener() {
    //  @Override
    //  public void onClick(final DialogInterface dialog, final int which) {
    //    final ZipInfoFragment fragment = new ZipInfoFragment();
    //    fragment.show(getSupportFragmentManager(), "help_fragment");
    //  }
    //}).setNegativeButton(R.string.cancel, null).show();

    OpenFileDialog mFileDialog = new OpenFileDialog(this)
            .setFilter(".*\\.zip")
            .setOpenDialogListener(new OpenFileListener());
    mFileDialog.show();
  }

  private void openFileChooser(int aFileType)
  {
    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType(aFileType == FILE_TYPE_ZIP ? DfuService.MIME_TYPE_ZIP : MIME_TYPE_TEXT);
    //intent.setType(DfuService.MIME_TYPE_ZIP);
    intent.addCategory(Intent.CATEGORY_OPENABLE);

    if (intent.resolveActivity(getPackageManager()) != null)
    {
      // file browser has been found on the device
      startActivityForResult(intent, REQUEST_SELECT_FILE);
    }
    //else
    //{
      // there is no any file browser app, let's try to download one
    //  final View customView = getLayoutInflater().inflate(R.layout.app_file_browser, null);
    //  final ListView appsList = (ListView) customView.findViewById(android.R.id.list);
    //  appsList.setAdapter(new FileBrowserAppsAdapter(this));
    //  appsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    //  appsList.setItemChecked(0, true);
    //  new AlertDialog.Builder(this).setTitle(R.string.dfu_alert_no_filebrowser_title).setView(customView)
    //          .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
    //            @Override
    //            public void onClick(final DialogInterface dialog, final int which) {
    //              dialog.dismiss();
    //            }
    //          }).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    //    @Override
    //    public void onClick(final DialogInterface dialog, final int which) {
    //      final int pos = appsList.getCheckedItemPosition();
    //      if (pos >= 0) {
    //        final String query = getResources().getStringArray(R.array.dfu_app_file_browser_action)[pos];
    //        final Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(query));
    //        startActivity(storeIntent);
    //      }
    //    }
    //  }).show();
    //}
  }


  public void onSetCfgFileBtnClicked(final View view)
  {
    printMessage("Set Config File Action Performed", true);
    mFileType = FILE_TYPE_CFG;
    //openFileChooser(mFileType);

    //mScriptTask = new aScriptTask();
    //mScriptTask.execute("");

    OpenFileDialog mFileDialog = new OpenFileDialog(this)
            .setFilter(".*\\.cfg")
            .setOpenDialogListener(new OpenFileListener());
    mFileDialog.show();
  }

  public void onSetTstFileBtnClicked(final View view)
  {
    printMessage("Set Testing File Action Performed :", true);
    mFileType = FILE_TYPE_TST;
    //openFileChooser(mFileType);

    //final byte[] mRxValue = new byte[8];
    //mRxValue[0] = 'R';
    //mRxValue[1] = 'X';
    //mRxValue[2] = 'V';
    //mRxValue[3] = 'A';
    //mRxValue[4] = 'L';
    //mRxValue[5] = '1';
    //mRxValue[6] = '2';
    //mRxValue[7] = '3';

    //if (mScriptTask != null)
    //{
    //  mScriptTask.putRxData(mRxValue);

    //  synchronized(mScriptTask)
    //  {
    //    mScriptTask.notify();
    //  }
    //}

    OpenFileDialog mFileDialog = new OpenFileDialog(this)
            .setFilter(".*\\.tst")
            .setOpenDialogListener(new OpenFileListener());
    mFileDialog.show();
  }

  /************************************************************************************************/
  /* Configuration/Testing Task */
  /************************************************************************************************/

  public class aScriptTask extends AsyncTask<String, String, String>
  {
    private final int  MODE_IDLE     = 0;
    private final int  MODE_CONFIG   = 1;
    private final int  MODE_TEST     = 2;
    private String     cTag          = "ScriptTask";
    private String     cRxData       = null;
    private String     cRxMsg;
    private byte[]     cTxBuffer;
    private int        cMode         = MODE_IDLE;
    private FileParser cParser       = null;
    private int        cCmdCount     = 0;
    private int        cCmdIndex     = 0;

    public synchronized void putRxData(byte[] aData)
    {
      //final byte[] mRxValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);

      //if (mScriptTask != null)
      //{
      //  synchronized(mScriptTask)
      //  {
      //    mScriptTask.notify();
      //  }
      //}

      try
      {
        cRxData = new String(aData, "UTF-8");
        Log.d(cTag, "Data Received -> " + cRxData);
      }
      catch(UnsupportedEncodingException e)
      {
        cRxData = null;
        Log.d(cTag, "Exception occured while data receiving: " + e.getMessage());
      }
    }

    private synchronized boolean sendMessage(String aMessage)
    {
      try
      {
        cTxBuffer = aMessage.getBytes("UTF-8");
        mUartService.writeRXCharacteristic(cTxBuffer);
        publishProgress("TX: " + aMessage);
        return true;
      }
      catch (UnsupportedEncodingException e)
      {
        Log.d(cTag, "Error while data send" + e.getMessage());
        return false;
      }
    }

    private String readMessage(int aTimeout)
    {
      cRxData = null;
      try
      {
        this.wait(aTimeout);
        if (cRxData != null)
        {
          publishProgress("RX: " + cRxData);
          Log.d(cTag, "Data Received: " + cRxData);
        }
        return cRxData;
      }
      catch (Exception e)
      {
        Log.d(cTag, "Read Message Exception Occured" + e.getMessage());
        return null;
      }
    }

    private void delay(int aTimeout, boolean aLog)
    {
      if(aTimeout == 0) return;

      try
      {
        if(aLog) publishProgress("P: " + aTimeout + " ms");
        Thread.sleep(aTimeout);
      }
      catch(InterruptedException e)
      {
        return;
      }
    }

    @Override
    protected void onPreExecute()
    {
      super.onPreExecute();

      switch(mFileType)
      {
        case FILE_TYPE_CFG:
          cMode = MODE_CONFIG;
          printMessage("Device configuration started...", true);
          break;
        case FILE_TYPE_TST:
          cMode = MODE_TEST;
          printMessage("Device testing started...", true);
          break;
        default:
          cMode = MODE_IDLE;
          printMessage("Wrong file!", true);
          break;
      }

      setUIState(TTC_BUSY);

      mProgressBar.setIndeterminate(true);
      mProgressBar.setProgress(0);

      Log.d(cTag, "Do Pre Execute : Server = ");
    }

    @Override
    protected synchronized String doInBackground(String... message)
    {
      Log.d(cTag, "Do In Background...");

      publishProgress(" - File = " + message[0]);

      switch(cMode)
      {
        case MODE_CONFIG:
          cParser = new ConfigFileParser(message[0]);
          break;
        case MODE_TEST:
          cParser = new TestFileParser(message[0]);
          break;
        case MODE_IDLE:
        default:
          return null;
      }

      if(!cParser.isValid()) return null;

      publishProgress(" - File Version = " + cParser.getFileVersion());

      /********************************************/
      publishProgress("SoftWare Version Request...");

      if(!sendMessage("VS")) return null;

      cRxMsg = readMessage(3000);

      if(cRxMsg == null) return null;

      delay(10, false);

      /********************************************/
      publishProgress("HardWare Version Request...");

      if(!sendMessage("VH")) return null;

      cRxMsg = readMessage(3000);

      if(cRxMsg == null) return null;

      delay(10, false);

      /********************************************/
      publishProgress("Performing command sequence...");
      FileParser.Command aCmd;
      cParser.selectFirstCommand();
      cCmdCount = cParser.getCommandsCount();
      cCmdIndex = 0;
      while((aCmd = cParser.getNextCommand()) != null)
      {
        sendMessage(aCmd.getCommand());
        readMessage(100);
        delay(aCmd.getPause(), true);
        cCmdIndex++;
      }

      return "Good!";
    }

    @Override
    protected void onProgressUpdate(String... values)
    {
      super.onProgressUpdate(values);

      printMessage(values[0], true);

      if(cCmdCount == 0)
      {
        mProgressBar.setIndeterminate(true);
        mProgressBar.setProgress(0);
      }
      else
      {
        mProgressBar.setIndeterminate(false);
        mProgressBar.setProgress(100 * cCmdIndex / cCmdCount);
      }


      Log.d(cTag, "Do On Progress");
    }

    @Override
    protected void onCancelled()
    {
      super.onCancelled();

      Log.d(cTag, "Do On Cancelled");
    }

    @Override
    protected void onPostExecute(String aResult)
    {
      super.onPostExecute(aResult);

      Log.d(cTag, "Do Post Execute");

      setUIState(TTC_READY);

      mProgressBar.setIndeterminate(false);
      mProgressBar.setProgress(0);

      if(aResult == null) printMessage("Wrong file!", true);

      printMessage("Done!", true);

      mScriptTask = null;
    }
  }

  /************************************************************************************************/
  /* Alternate Open File Chooser */
  /************************************************************************************************/

  private class OpenFileListener implements OpenFileDialog.OpenDialogListener
  {
    @Override
    public void OnSelectedFile(String aFileName)
    {
      //BitmapFactory.Options mOptions = new BitmapFactory.Options();
      //mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
      //mOptions.inScaled = false;
      //mOptions.inJustDecodeBounds = true;
      //mBitmap = BitmapFactory.decodeFile(aFileName, mOptions);

      //if ((mOptions.outWidth != 48) || (mOptions.outHeight != 32))
      //{
      //  Toast.makeText(getApplicationContext(), "Wrond Image Dimentions!", Toast.LENGTH_LONG).show();
      //}
      //else
      //{
      //  mOptions.inJustDecodeBounds = false;
      //  mBitmap = BitmapFactory.decodeFile(aFileName, mOptions);
      //  mImageView.setImageBitmap(mBitmap);
      //}

      // Clear previous data
      //mFwFilePath = null;
      //mCfgFilePath = null;
      //mTstFilePath = null;
      //mFileUri = null;

      final File file = new File(aFileName);

      switch(mFileType)
      {
        case FILE_TYPE_ZIP:
          mFwFilePath = aFileName;
          break;
        case FILE_TYPE_CFG:
          mCfgFilePath = aFileName;
          break;
        case FILE_TYPE_TST:
          mTstFilePath = aFileName;
          break;
        case FILE_TYPE_NONE:
        default:
          break;
      }

      updateFileInfo(file.getName(), file.length(), aFileName, mFileType);
    }
  }

  /************************************************************************************************/
  /* Alternate Open File Chooser */
  /************************************************************************************************/

  public void onConfigDeviceBtnClicked(final View view)
  {
    if(mCfgFilePath != null)
    {
      mScriptTask = new aScriptTask();
      mScriptTask.execute(mCfgFilePath);
    }
    else
    {
      printMessage("Select configuration file first...", false);
    }
  }

  public void onTestDeviceBtnClicked(final View view)
  {
    if(mTstFilePath != null)
    {
      mScriptTask = new aScriptTask();
      mScriptTask.execute(mTstFilePath);
    }
    else
    {
      printMessage("Select test file first...", false);
    }
  }
}
