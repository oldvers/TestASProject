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

package com.nordicsemi.nrfUARTv2;




import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;


import com.nordicsemi.nrfUARTv2.UartService;
import com.nordicsemi.nrfUARTv2.dfu.DfuService;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;

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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class MainActivity extends Activity implements
        RadioGroup.OnCheckedChangeListener, LoaderCallbacks<Cursor>
{
  private static final int    REQUEST_SELECT_DEVICE = 1;
  private static final int    REQUEST_ENABLE_BT     = 2;
  private static final int    REQUEST_SELECT_FILE   = 3;
  private static final int    UART_PROFILE_READY    = 10;
  public  static final String TAG = "TeepTracController";
  private static final int    UART_PROFILE_CONNECTED = 20;
  private static final int    UART_PROFILE_DISCONNECTED = 21;
  private static final int    STATE_OFF = 10;

  private static final String EXTRA_URI = "uri";

  TextView                     mRemoteRssiVal;
  RadioGroup                   mRg;
  private int                  mState = UART_PROFILE_DISCONNECTED;
  private UartService          mService = null;
  private BluetoothDevice      mDevice = null;
  private BluetoothAdapter     mBtAdapter = null;
  private ListView             messageListView;
  private ArrayAdapter<String> listAdapter;
  private Button               btnConnectDisconnect, btnSend, btnDAT, btnUpgrade, btnSetFile;
  private EditText             edtMessage;

  private TextView             mDfuState;
  private ProgressBar          mDfuProgress;

  private String mFilePath;
  private Uri mFileStreamUri;

  private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter()
  {
    @Override
    public void onDeviceConnecting(final String deviceAddress)
    {
      mDfuProgress.setIndeterminate(true);
      mDfuState.setText(R.string.dfu_status_connecting);
      printMessage(getString(R.string.dfu_status_connecting), true);
    }

    @Override
    public void onDfuProcessStarting(final String deviceAddress)
    {
      mDfuProgress.setIndeterminate(true);
      mDfuState.setText(R.string.dfu_status_starting);
      printMessage(getString(R.string.dfu_status_starting), true);
    }

    @Override
    public void onEnablingDfuMode(final String deviceAddress)
    {
      mDfuProgress.setIndeterminate(true);
      mDfuState.setText(R.string.dfu_status_switching_to_dfu);
      printMessage(getString(R.string.dfu_status_switching_to_dfu), true);
    }

    @Override
    public void onFirmwareValidating(final String deviceAddress)
    {
      mDfuProgress.setIndeterminate(true);
      mDfuState.setText(R.string.dfu_status_validating);
      printMessage(getString(R.string.dfu_status_validating), true);
    }

    @Override
    public void onDeviceDisconnecting(final String deviceAddress)
    {
      mDfuProgress.setIndeterminate(true);
      mDfuState.setText(R.string.dfu_status_disconnecting);
      printMessage(getString(R.string.dfu_status_disconnecting), true);
    }

    @Override
    public void onDfuCompleted(final String deviceAddress)
    {
      mDfuState.setText(R.string.dfu_status_completed);

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
      mDfuState.setText(R.string.dfu_status_aborted);
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
      mDfuProgress.setIndeterminate(false);
      mDfuProgress.setProgress(percent);
      mDfuState.setText(getString(R.string.dfu_uploading_percentage, percent));

      //if (partsTotal > 1)
      //  mTextUploading.setText(getString(R.string.dfu_status_uploading_part, currentPart, partsTotal));
      //else
      //  mTextUploading.setText(R.string.dfu_status_uploading);
    }

    @Override
    public void onError(final String deviceAddress, final int error, final int errorType, final String message)
    {
      //showErrorMessage(message);
      mDfuState.setText(getString(R.string.dfu_uploading_error, 0));
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

        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);

        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);

        btnConnectDisconnect = (Button) findViewById(R.id.btn_select);
        btnSend              = (Button) findViewById(R.id.sendButton);
        btnDAT               = (Button) findViewById(R.id.datButton);

        //DFU GUI
        btnUpgrade           = (Button) findViewById(R.id.datButton);
        btnSetFile           = (Button) findViewById(R.id.setFileButton);
        mDfuProgress         = (ProgressBar) findViewById(R.id.dfuProgressBar);
        mDfuState            = (TextView) findViewById(R.id.dfuStateTextView);

        edtMessage = (EditText) findViewById(R.id.sendText);
        service_init();



        // Handler Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(!mBtAdapter.isEnabled())
                {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else
                {
                    if(btnConnectDisconnect.getText().equals("Connect"))
                    {

                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else
                    {
                        //Disconnect button pressed
                        if(mDevice != null)
                        {
                            mService.disconnect();

                        }
                    }
                }
            }
        });

        // Handler Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	EditText editText = (EditText) findViewById(R.id.sendText);
            	String message = editText.getText().toString();
            	byte[] value;
				try {
					//send data to service
					value = message.getBytes("UTF-8");
					mService.writeRXCharacteristic(value);
					//Update the log with time stamp
					String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
					listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
               	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
               	 	edtMessage.setText("");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

            }
        });






        // Handler DAT Button
        btnDAT.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                //EditText editText = (EditText) findViewById(R.id.sendText);
                String message = "DAT";
                byte[] value;
                try
                {
                    //send data to service
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);
                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                }
                catch (UnsupportedEncodingException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });

        // Set initial UI state

  }

  private void printMessage(String aMsg, boolean aInsertDate)
  {
    //Update the log with time stamp
    String dateTime = DateFormat.getTimeInstance().format(new Date());
    if (aInsertDate)
    {
      listAdapter.add("[" + dateTime + "] " + aMsg);
    }
    else
    {
      listAdapter.add("           " + aMsg);
    }
    //listAdapter.add(aMsg);
    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
  }

    //UART service connected/disconnected
  private ServiceConnection mServiceConnection = new ServiceConnection()
  {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
        		mService = ((UartService.LocalBinder) rawBinder).getService();
        		Log.d(TAG, "onServiceConnected mService= " + mService);
        		if (!mService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }

        }

        public void onServiceDisconnected(ComponentName classname) {
       ////     mService.disconnect(mDevice);
        		mService = null;
        }
  };

  private Handler mHandler = new Handler()
  {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
  };

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
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            Log.d(TAG, "UART_CONNECT_MSG");
            btnConnectDisconnect.setText("Disconnect");
            edtMessage.setEnabled(true);
            btnSend.setEnabled(true);
            btnDAT.setEnabled(true);
            btnSetFile.setEnabled(true);
            btnUpgrade.setEnabled(true);
            ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - Ready");
            listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
         	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
            mState = UART_PROFILE_CONNECTED;
          }
        });
      }

      //*********************//
      if (action.equals(UartService.ACTION_GATT_DISCONNECTED))
      {
        runOnUiThread(new Runnable()
        {
          public void run()
          {
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            Log.d(TAG, "UART_DISCONNECT_MSG");
            btnConnectDisconnect.setText("Connect");
            edtMessage.setEnabled(false);
            btnSend.setEnabled(false);
            btnDAT.setEnabled(false);
            btnUpgrade.setEnabled(false);
            btnSetFile.setEnabled(false);
            ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
            listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
            mState = UART_PROFILE_DISCONNECTED;
            mService.close();
            //setUiState();
          }
        });
      }


      //*********************//
      if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED))
      {
        mService.enableTXNotification();
      }


      //*********************//
      if (action.equals(UartService.ACTION_DATA_AVAILABLE))
      {
        final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
        runOnUiThread(new Runnable()
        {
          public void run()
          {
            try
            {
              String text = new String(txValue, "UTF-8");
              String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
              listAdapter.add("["+currentDateTimeString+"] RX: "+text);
              messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

            }
            catch (Exception e)
            {
              Log.e(TAG, e.toString());
            }
          }
        });
      }


      //*********************//
      if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART))
      {
       	showMessage("Device doesn't support UART. Disconnecting");
      	mService.disconnect();
      }

    }
  };

  private void service_init()
  {
    Intent bindIntent = new Intent(this, UartService.class);
    bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

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

        try {
        	LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
      mService= null;

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
    DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
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
    DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);

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
    //if(resultCode != RESULT_OK) return;

    switch (requestCode)
    {
      case REQUEST_SELECT_DEVICE:
        //When the DeviceListActivity return, with the selected device address
        if (resultCode == Activity.RESULT_OK && data != null)
        {
          String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
          mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

          Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
          ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
          mService.connect(deviceAddress);
        }
        break;

      case REQUEST_ENABLE_BT:
        // When the request to enable Bluetooth returns
        if (resultCode == Activity.RESULT_OK)
        {
          Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
        }
        else
        {
          // User did not enable Bluetooth or an error occurred
          Log.d(TAG, "BT not enabled");
          Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
          finish();
        }
        break;

      case REQUEST_SELECT_FILE:
        // clear previous data
        //mFileType = mFileTypeTmp;
        mFilePath = null;
        mFileStreamUri = null;

        // and read new one
        final Uri uri = data.getData();
			  /*
			   * The URI returned from application may be in 'file' or 'content' schema. 'File' schema allows us to create a File object and read details from if
			   * directly. Data from 'Content' schema must be read by Content Provider. To do that we are using a Loader.
			   */
        if (uri.getScheme().equals("file"))
        {
          // the direct path to the file has been returned
          final String path = uri.getPath();
          final File file = new File(path);
          mFilePath = path;

          //printMessage("Set File Action :", true);
          //printMessage("           Path = " + mFilePath, false);

          updateFileInfo(file.getName(), file.length(), mFilePath);
        }
        else if (uri.getScheme().equals("content"))
        {
          // an Uri has been returned
          mFileStreamUri = uri;
          // if application returned Uri for streaming, let's us it. Does it works?
          // FIXME both Uris works with Google Drive app. Why both? What's the difference? How about other apps like DropBox?
          final Bundle extras = data.getExtras();
          if (extras != null && extras.containsKey(Intent.EXTRA_STREAM))
               mFileStreamUri = extras.getParcelable(Intent.EXTRA_STREAM);

          // file name and size must be obtained from Content Provider
          final Bundle bundle = new Bundle();
          bundle.putParcelable(EXTRA_URI, uri);
          getLoaderManager().restartLoader(REQUEST_SELECT_FILE, bundle, this);
        }
        break;

      default:
        Log.e(TAG, "wrong request code");
        break;
    }
  }


  @Override
  public void onCheckedChanged(RadioGroup group, int checkedId)
  {
    //
  }


  private void showMessage(String msg)
  {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
  }


  @Override
  public void onBackPressed()
  {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.popup_title)
            .setMessage(R.string.popup_message)
            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
   	                finish();
                }
            })
            .setNegativeButton(R.string.popup_no, null)
            .show();
        }
  }






  public void onUploadClicked(final View view)
  {
    if (isDfuServiceRunning())
    {
      showUploadCancelDialog();
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

    final DfuServiceInitiator starter = new DfuServiceInitiator(mDevice.getAddress())
            .setDeviceName(mDevice.getName())
            .setKeepBond(false) //keepBond);
    //if (mFileType == DfuService.TYPE_AUTO)
            .setZip(null, mFilePath);
    //else {
    //  starter.setBinOrHex(mFileType, mFileStreamUri, mFilePath).setInitFile(mInitFileStreamUri, mInitFilePath);
    //}
    starter.start(this, DfuService.class);
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
    mFilePath = null;
    mFileStreamUri = null;
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
        filePath = data.getString(dataIndex /* 2 DATA */);
      if (!TextUtils.isEmpty(filePath))
        mFilePath = filePath;

      updateFileInfo(fileName, fileSize, mFilePath);
    }
    else
    {
      //mFileNameView.setText(null);
      //mFileTypeView.setText(null);
      //mFileSizeView.setText(null);
      mFilePath = null;
      mFileStreamUri = null;
      //mFileStatusView.setText(R.string.dfu_file_status_error);
      //mStatusOk = false;
    }
  }


  private void updateFileInfo(final String fileName, final long fileSize, final String filePath)
  {
    //mFileNameView.setText(fileName);
    //switch (fileType) {
    //  case DfuService.TYPE_AUTO:
    //    mFileTypeView.setText(getResources().getStringArray(R.array.dfu_file_type)[0]);
    //    break;
    //  case DfuService.TYPE_SOFT_DEVICE:
    //    mFileTypeView.setText(getResources().getStringArray(R.array.dfu_file_type)[1]);
    //    break;
    //  case DfuService.TYPE_BOOTLOADER:
    //    mFileTypeView.setText(getResources().getStringArray(R.array.dfu_file_type)[2]);
    //    break;
    //  case DfuService.TYPE_APPLICATION:
    //    mFileTypeView.setText(getResources().getStringArray(R.array.dfu_file_type)[3]);
    //    break;
    //}
    //mFileSizeView.setText(getString(R.string.dfu_file_size_text, fileSize));
    //printMessage("Set File Action :", true);

    printMessage("- Name = " + fileName, false);
    printMessage("- Type = " + "ZIP", false);
    printMessage("- Path = " + filePath, false);
    printMessage("- Size = " + fileSize + " Bytes", false);

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
  public void onSelectFileClicked(final View view)
  {
    printMessage("Set File Action Performed :", true);
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
        openFileChooser();
    //  }
    //}).setNeutralButton(R.string.dfu_file_info, new DialogInterface.OnClickListener() {
    //  @Override
    //  public void onClick(final DialogInterface dialog, final int which) {
    //    final ZipInfoFragment fragment = new ZipInfoFragment();
    //    fragment.show(getSupportFragmentManager(), "help_fragment");
    //  }
    //}).setNegativeButton(R.string.cancel, null).show();
  }

  private void openFileChooser()
  {
    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    //intent.setType(mFileTypeTmp == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM);
    intent.setType(DfuService.MIME_TYPE_ZIP);
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
}
