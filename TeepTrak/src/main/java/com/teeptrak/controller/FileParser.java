package com.teeptrak.controller;


public abstract class FileParser
{
  public abstract String getFileVersion();
  public abstract boolean isValid();

  public class Command
  {
    private String mCommand;
    private int    mPause;

    public Command()
    {
      mCommand = null;
      mPause = 0;
    }

    public String getCommand()
    {
      return mCommand;
    }

    public int getPause()
    {
      return mPause;
    }

    public void setCommand(String aValue)
    {
      mCommand = aValue;
    }

    public void setPause(int aValue)
    {
      mPause = aValue;
    }
  }

  public abstract int getCommandsCount();
  public abstract void selectFirstCommand();
  public abstract Command getNextCommand();
}
