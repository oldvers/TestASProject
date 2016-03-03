package com.teeptrak.controller;


import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class CompatTable
{
  public static final   int  CONFIG = 0;
  public static final   int  TEST   = 1;

  private       boolean      mValid = false;
  private final List<Row>    mRows  = new ArrayList<Row>();

  public class Row
  {
    private String mHW;
    private String mSW;
    private String mCfg;
    private String mTst;

    public Row(JSONObject jRow) throws JSONException
    {
      setHW(jRow.get("HW"));
      setSW(jRow.get("SW"));
      setCfg(jRow.get("CFG"));
      setTst(jRow.get("TST"));
    }

    public String getHW()
    {
      return mHW;
    }

    public String getSW()
    {
      return mSW;
    }

    public String getCfg()
    {
      return mCfg;
    }

    public String getTst()
    {
      return mTst;
    }

    private void setHW(Object aHW)
    {
      if((aHW != null) && (aHW instanceof String) && (!((String)aHW).isEmpty()))
      {
        mHW = (String)aHW;
      }
      else
      {
        mHW = "";
      }
    }

    private void setSW(Object aSW)
    {
      if((aSW != null) && (aSW instanceof String) && (!((String)aSW).isEmpty()))
      {
        mSW = (String)aSW;
      }
      else
      {
        mSW = "";
      }
    }

    private void setCfg(Object aCfg)
    {
      if((aCfg != null) && (aCfg instanceof String) && (!((String)aCfg).isEmpty()))
      {
        mCfg = (String)aCfg;
      }
      else
      {
        mCfg = "";
      }
    }

    private void setTst(Object aTst)
    {
      if((aTst != null) && (aTst instanceof String) && (!((String)aTst).isEmpty()))
      {
        mTst = (String)aTst;
      }
      else
      {
        mTst = "";
      }
    }
  }

  public boolean isValid()
  {
    return mValid;
  }

  public CompatTable(Resources res) throws Exception
  {
    mValid = false;

    InputStream is = res.openRawResource(R.raw.compatibility);

    Writer writer = new StringWriter();

    char[] buffer = new char[1024];

    try
    {
      Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

      int n;

      while((n = reader.read(buffer)) != -1)
      {
        writer.write(buffer, 0, n);
      }

      JSONObject jTable = new JSONObject(writer.toString());

      JSONArray jRows = jTable.getJSONArray("CompatibilityTable");

      Row row;

      if((jRows != null) && jRows.length() > 0)
      {
        for(int i = 0; i < jRows.length() - 1; i++)
        {
          row = new Row(jRows.getJSONObject(i));
          mRows.add(row);
        }

        mValid = true;
      }
    }
    finally
    {
      is.close();
    }
  }

  public boolean checkValidity(String aHW, String aSW, String aFile, int aFileType)
  {
    if(isValid())
    {
      String cFile;

      for(int i = 0; i < mRows.size() - 1; i++)
      {
        switch(aFileType)
        {
          case CONFIG:
            cFile = mRows.get(i).getCfg();
            break;
          case TEST:
            cFile = mRows.get(i).getTst();
            break;
          default:
            return false;
        }

        if(mRows.get(i).getHW().equals(aHW) &&
           mRows.get(i).getSW().equals(aSW) &&
           cFile.equals(aFile))
          return true;
      }
    }

    return false;
  }
}
