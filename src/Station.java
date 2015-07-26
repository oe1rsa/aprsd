/* 
 * Copyright (C) 2014 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  
  
/**
 * APRS station. 
 */
public class Station extends AprsPoint implements Serializable, Cloneable
{

   public static class Status implements Serializable
   {
       public Date time;
       public String text;
       public Status(Date t, String txt)
          { time = t; text = txt; }
   }

 
    public static void init(ServerAPI api)
      { int exptime = api.getIntProperty("aprs.expiretime", 60);
        setExpiretime(exptime * 60 * 1000);
      }
    
    
    /*
     * Attributes of a station record (APRS data)
     */
    private String    _callsign; 
    private Status    _status;


    
    /* 
     * Other variables (presentation, storage, etc.)
     */
    private String      _pathinfo; 
    private int         _report_ignored = 0;
    private boolean     _igate, _wdigi;
    private Date        _infra_updated = null;
    private Source      _source;
       
       
    public Station(String id)
       { super(null); _callsign = id; _trailcolor = _colTab.nextColour(); }
        
    public Object clone() throws CloneNotSupportedException
       { return super.clone(); }
                      
           

    @Override public String getIdent()
       { return _callsign; }
       
    protected void setId(String id)
       { _callsign = id; }
       
    public String getPathInfo()
       { return _pathinfo; }
       
    public void setPathInfo(String p)
       { _pathinfo = p; }
       
    public Set<String> getTrafficFrom()
       {  return _db.getRoutes().getToEdges(getIdent()); }
       
                       
    public Set<String> getTrafficTo()
       { return _db.getRoutes().getFromEdges(getIdent());}
              
              
    public boolean isInfra()
       { return getTrafficFrom() != null && !getTrafficFrom().isEmpty(); }
       
       
    /**
     * Reset infrastructure settings if older than 7 days 
     */
    private void expireInfra()
    {
        Date now = new Date();
        if (_infra_updated != null && 
            _infra_updated.getTime() + 1000*60*60*24*7 < now.getTime())
          _igate = _wdigi = false; 
    } 
    
    
    public void setSource(Source src)
       { _source = src; }
   
    @Override public Source getSource()
       { return _source; }
       
    
    public boolean isIgate()
       { expireInfra(); 
         return _igate; }
       
       
    public void setIgate(boolean x)
       { _infra_updated = new Date(); 
         _igate = x; }
       
       
    public boolean isWideDigi()
       { expireInfra(); 
         return _wdigi; }
       
       
    public void setWideDigi(boolean x)
       { _infra_updated = new Date(); 
         _wdigi = x; }       
       
    

    public synchronized void setStatus(Date t, String txt)
    {
        if (t==null)  
           t = new Date(); 
        _status = new Status(t, txt);
    }
    
    /* Vi kan kanskje legge inn en sjekk om statusrapporten er for gammel */
    public Status getStatus()
        { return _status; }
    
    
    @Override public synchronized void reset()
    {  
        _db.getRoutes().removeNode(this.getIdent());
        super.reset(); 
    }
     
            
    protected void checkForChanges()
    { 
        if (_trail.itemsExpired()) 
           setChanging(); 
    }     
     
     
     
    @Override public synchronized void setUpdated(Date ts)
      { _updated = ts; _expired = false; }
      
      
        
    public synchronized void update(Date ts, AprsHandler.PosData pd, String descr, String pathinfo)
    { 
        if (_position != null && _updated != null)
        { 
           if (ts != null)
           {
              /* Time distance in seconds */
              long tdistance = (ts.getTime() - _updated.getTime()) / 1000;          
              
              /* Downsample. Time resolution is 10 seconds or more */
              if (tdistance < 5)
                 return; 
                 
              /*
               * If distance/time implies a speed more than a certain limit (500km/h), 
               * ignore the report. But not more than 3 times. Clear history if
               * ignored 3 times. 
               * FIXME: speed limit should be configurable.
               */
              if ( _report_ignored < 2 && tdistance > 0 && 
                    distance(pd.pos)/tdistance > (500 * 0.27778)) 
              {
                 System.out.println("*** Ignore report moving beyond speed limit (500km/h)");
                 _report_ignored++;
                 return;
              }
              if (_report_ignored >= 2) {
                 _trail.clear();
                 _db.getRoutes().removeOldEdges(getIdent(), ts);
              }
              
              /* If report is older than the previous one, just save it in the 
               * history 
               */
               if (tdistance < 0 && _trail.add(ts, pd.pos, pd.speed, pd.course, pathinfo)) {
                   System.out.println("*** Old report - update trail");
                   setChanging(); 
                   return;
               }            
           }            
           _report_ignored = 0;
                       

           if (saveToTrail(ts, pd.pos, _pathinfo)) 
               _db.getRoutes().removeOldEdges(getIdent(), _trail.oldestPoint());
           
        }
        updatePosition(ts, pd.pos, pd.ambiguity);
        
        setSpeed(pd.speed);
        setCourse(pd.course);
        setAltitude((int) pd.altitude);
        _pathinfo = pathinfo; 
       
        setDescr(descr); 
        
        if (_expired) {
            _expired = false;
            setChanging();
        }
        
        if (pd.symbol != 0 && pd.symtab != 0 && (pd.symbol != _symbol || pd.symtab != _altsym))
        {
            if (pd.symbol != 0)  _symbol = pd.symbol;
            if (pd.symtab != 0)  _altsym = pd.symtab;
            setChanging();
        }
        
        isChanging(); 
    }
    

    
    @Override
    public synchronized boolean _expired()
    {
        if (!super._expired()) 
            return false;
        if (!_db.getOwnObjects().mayExpire(this))
            return false;
        _db.getRoutes().removeNode(this.getIdent());
        return true; 
    }
    

}
