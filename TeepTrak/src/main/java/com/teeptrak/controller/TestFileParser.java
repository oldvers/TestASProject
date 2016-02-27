package com.teeptrak.controller;


import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class TestFileParser extends FileParser
{
  private final String             mTag          = "TstFileParser";
  private       String             mFileVersion  = "";
  private       ArrayList<Command> mCommands     = null;
  private       int                mCommandIndex = 0;
  private       boolean            mValid        = false;

  public TestFileParser(String aFileName)
  {
    File mFile;
    FileReader mFileReader;
    BufferedReader mBuffReader;

    Log.d(mTag, "File = " + aFileName);

    mFile = new File(aFileName);

    if(mFile.exists())
    {
      try
      {
        mFileReader = new FileReader(mFile);
        mBuffReader = new BufferedReader(mFileReader);

        mCommands = new ArrayList<Command>();

        String  aStr     = null;
        Command mCommand = null;
        int     mPause   = 0;

        while((aStr = mBuffReader.readLine()) != null)
        {
          if(aStr.equals("")) continue;

          aStr = aStr.toUpperCase();

          if(!aStr.contains("PAUSE"))
          {
            mCommand = new Command();
            mCommand.setCommand(aStr);
            mCommands.add(mCommand);
          }
          else
          {
            aStr = aStr.substring(aStr.indexOf("PAUSE") + 5).trim();

            try
            {
              mPause = 1000 * Integer.parseInt(aStr);
            }
            catch(Exception e)
            {
              mPause = 0;
            }

            if((mCommands.size() > 0))
            {
              mCommands.get(mCommands.size() - 1).setPause(mPause);
            }
          }
        }
        mFileReader.close();

        if(!mCommands.isEmpty() &&
                mCommands.get(0).getCommand().contains("*TEEPTRAK") &&
                mCommands.get(0).getCommand().contains("TEST") &&
                mCommands.get(0).getCommand().contains("VERSION"))
        {
          mFileVersion = mCommands.get(0).getCommand().substring(
                  mCommands.get(0).getCommand().indexOf("VERSION") + 7);
          if(!mFileVersion.equals(""))
          {
            mFileVersion = mFileVersion.replace(" ", "").trim();
            if(mFileVersion.length() == 2)
            {
              mCommands.remove(0);
              mValid = true;
            }
          }
        }
      }
      catch (Exception e)
      {
        Log.e(mTag, "Error : " + e.getMessage());
      }
    }
  }

  @Override
  public String getFileVersion()
  {
    return mFileVersion;
  }

  @Override
  public boolean isValid()
  {
    return mValid;
  }

  @Override
  public int getCommandsCount()
  {
    return mCommands.size();
  }

  @Override
  public void selectFirstCommand()
  {
    mCommandIndex = 0;
  }

  @Override
  public Command getNextCommand()
  {
    Command res;

    if((!mCommands.isEmpty()) && (mCommandIndex < mCommands.size()))
    {
      res = mCommands.get(mCommandIndex);
      mCommandIndex++;
    }
    else
    {
      res = null;
    }

    return res;
  }
}
