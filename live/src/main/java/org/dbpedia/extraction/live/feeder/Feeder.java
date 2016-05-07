package org.dbpedia.extraction.live.feeder;

import org.dbpedia.extraction.live.queue.LiveQueue;
import org.dbpedia.extraction.live.queue.LiveQueueItem;
import org.dbpedia.extraction.live.queue.LiveQueuePriority;
import org.dbpedia.extraction.live.util.ExceptionUtil;
import org.dbpedia.extraction.live.util.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Dimitris Kontokostas
 * Date: 2/27/13
 * Time: 12:58 PM
 * This is an abstract class for feeders.
 */
public abstract class Feeder extends Thread {

    protected static Logger logger;
    protected final String feederName;
    protected final LiveQueuePriority queuePriority;

    protected final String defaultStartTime;    //"2011-04-01T15:00:00Z";
    protected final File latestProcessDateFile;
    protected String latestProcessDate;

    private volatile boolean keepRunning = true;

    public Feeder(String feederName, LiveQueuePriority queuePriority, String defaultStartTime, String folderBasePath) {
        this.feederName = feederName;
        this.setName("Feeder_"+feederName);
        logger = LoggerFactory.getLogger(feederName);
        this.queuePriority = queuePriority;

        this.defaultStartTime = defaultStartTime;   //"2011-04-01T15:00:00Z";
        latestProcessDateFile = new File(folderBasePath + feederName + ".dat");
        getLatestProcessedDate();
    }

    public LiveQueuePriority getQueuePriority(){
        return queuePriority;
    }

    protected abstract void initFeeder();

    /*
    * Starts the feeder (it can only start once
    * */
    public void startFeeder() {
        if (keepRunning) {
            initFeeder();
            start();
        }
    }

    /*
    * Stops the feeder from running gracefully
    * */
    public void stopFeeder(String date) {
        keepRunning = false;
        setLatestProcessedDate(date);
    }

    /*
    * Reads the latest process date from the file location. Reverts to default on error
    * */
    public String getLatestProcessedDate() {
        latestProcessDate = defaultStartTime;
        try {
            if (!latestProcessDateFile.exists()) {
                //latestProcessDateFile.mkdirs();
                setLatestProcessedDate(defaultStartTime);
            } else {
                latestProcessDate = (Files.readFile(latestProcessDateFile)).trim();
            }
        } catch (Exception exp) {
            logger.error(ExceptionUtil.toString(exp), exp);
        }
        if (latestProcessDate.isEmpty()) {
            latestProcessDate = defaultStartTime;
            setLatestProcessedDate(latestProcessDate);
        }
        logger.warn("Resuming from date: " + latestProcessDate);
        return latestProcessDate;
    }

    /*
    * Updates the latest process date to file
    * */
    public synchronized void setLatestProcessedDate(String date) {
        if (date == null || date.equals(""))
            date = latestProcessDate;

        //Files.createFile(latestProcessDateFile, date);
    }

    protected abstract Collection<LiveQueueItem> getNextItems();

    public void run() {
        int counter = 0;
        while (keepRunning) {
            try {
                for (LiveQueueItem item : getNextItems()) {
                    handleFeedItem(item);
                }
            } catch (java.lang.OutOfMemoryError exp) {
                logger.error(ExceptionUtil.toString(exp), exp);
                throw new RuntimeException("OutOfMemory Error", exp);
            } catch (Exception exp) {
                logger.error(ExceptionUtil.toString(exp), exp);
                // On error re-initiate feeder
                initFeeder();
            }
            if (counter % 500 == 0) {
                setLatestProcessedDate(null);
                counter = 0;
            }
            counter ++;
        }
    }

    /* This function should be overwritten by sub classes */
    protected void handleFeedItem(LiveQueueItem item) {
        addPageIDtoQueue(item);
    }

    protected void addPageIDtoQueue(LiveQueueItem item) {
        item.setStatQueueAdd(-1);
        item.setPriority(this.queuePriority);
        LiveQueue.add(item);
        latestProcessDate = item.getModificationDate();
    }
}
