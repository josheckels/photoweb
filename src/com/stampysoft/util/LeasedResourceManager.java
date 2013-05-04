package com.stampysoft.util;

import java.util.Iterator;
import java.util.Vector;

/**
 * Resource manager that lets multiple threads share a collection of resources.
 * It handles resources dying, as well as enforcing a lease period.  A thread,
 * the slumlord, periodically checks that no leases are overdue.  If they are
 * it closes it out from under them and lets a waiting thread allocate
 * a new resource.
 */

public abstract class LeasedResourceManager
{

    /**
     * Resources in the pool that are available for use
     */
    private Vector mAvailable = new Vector();

    /**
     * Resources that have been leased out to a client
     */
    private Vector mLeases = new Vector();

    /**
     * Number of milliseconds a lease should last
     */
    private long mLeaseLength;

    /**
     * Number of milliseconds between checks if there are any overdue leases
     */
    private long mEvictionFrequency;

    /**
     * Number of resources that should be allocated to the pool
     */
    private int mResourceCount;

    public LeasedResourceManager(int resourceCount, long leaseLength)
    {
        this(resourceCount, leaseLength, leaseLength / 4);
    }

    public LeasedResourceManager(int resourceCount, long leaseLength, long evictionFrequency)
    {
        mLeaseLength = leaseLength;
        mEvictionFrequency = evictionFrequency;
        mResourceCount = resourceCount;

        Slumlord slumlord = new Slumlord();
        slumlord.start();

        System.runFinalizersOnExit(true);
    }


    /**
     * Can optionally be called to initialize the resource pool.  Only really
     * useful if there's a huge cost to initializing resources that needs
     * to be avoided on the first check out
     */
    protected void initResources() throws ResourceManagerException
    {
        synchronized (mAvailable)
        {
            while (getActiveCount() < mResourceCount)
            {
                mAvailable.addElement(getNewResource());
            }
        }
    }


    /**
     * Number of resources currently available
     */
    public int getAvailableCount()
    {
        synchronized (mAvailable)
        {
            return mAvailable.size();
        }
    }

    /**
     * Total number of resources currently in the pool, checked out or checked in
     */
    public int getActiveCount()
    {
        synchronized (mAvailable)
        {
            return mLeases.size() + getAvailableCount();
        }
    }

    /**
     * Grab a resource from the pool or block until there's one available
     */
    public Object checkOut() throws ResourceNotAvailableException
    {
        Object resource = null;

        while (true)
        {
            synchronized (mAvailable)
            {
                // Replenish the pool if it's low
                try
                {
                    while (getActiveCount() < mResourceCount)
                    {
                        mAvailable.addElement(getNewResource());
                        mAvailable.notify();
                    }
                }
                catch (ResourceManagerException e)
                {
                    logException(e);
                }

                // While there's some available, grab one, see if it's alive,
                // and return it.
                while (getAvailableCount() > 0)
                {
                    resource = mAvailable.remove(0);

                    try
                    {
                        if (isAlive(resource))
                        {
                            mLeases.addElement(new Lease(resource));
                            return resource;
                        }
                        else
                        {
                            try
                            {
                                releaseResource(resource);
                            }
                            catch (ResourceManagerException e)
                            {
                                logException(e);
                            }
                        }
                    }
                    catch (ResourceManagerException e)
                    {
                        logException(e);
                        try
                        {
                            releaseResource(resource);
                        }
                        catch (ResourceManagerException e2)
                        {
                            logException(e2);
                        }
                    }
                }

                // No active resources anywhere, let the client know they're out of luck
                if (getActiveCount() == 0)
                {
                    mAvailable.notify();
                    throw new ResourceNotAvailableException("Unable to obtain resource");
                }

                // Wait for an active resource, all of which are currently leased
                try
                {
                    mAvailable.wait();
                }
                catch (InterruptedException e)
                {
                }
            }
        }
    }

    /**
     * Clean up any open resources when the VM shuts down
     */
    public void finalize()
    {
        Iterator leases = mLeases.iterator();
        while (leases.hasNext())
        {
            Lease lease = (Lease) leases.next();
            try
            {
                releaseResource(lease.getResource());
            }
            catch (ResourceManagerException e)
            {
                logException(e);
            }
        }

        Iterator available = mAvailable.iterator();
        while (available.hasNext())
        {
            try
            {
                releaseResource(available.next());
            }
            catch (ResourceManagerException e)
            {
                logException(e);
            }
        }
    }

    /**
     * Return a resource to the pool.  Expect an exception if you kept it too long
     * or never got it from the pool in the first place
     */
    public void checkIn(Object resource) throws ResourceManagerException
    {
        synchronized (mAvailable)
        {
            Iterator leases = mLeases.iterator();
            while (leases.hasNext())
            {
                Lease lease = (Lease) leases.next();
                if (lease.getResource() == resource)
                {
                    leases.remove();
                    mAvailable.addElement(resource);
                    mAvailable.notify();
                    return;
                }
            }
        }
        throw new ResourceNotLeasedException("Either the lease on " + resource + " has expired or it was never a leased resource.");
    }

    /**
     * Simple wrapper around a resource object to store its expiration
     */
    public class Lease
    {
        private long mExpiration;

        private Object mResource;

        public Lease(Object resource)
        {
            mResource = resource;
            mExpiration = System.currentTimeMillis() + mLeaseLength;
        }

        public long getExpiration()
        {
            return mExpiration;
        }

        public Object getResource()
        {
            return mResource;
        }
    }

    /**
     * The SLUMLORD makes sure nobody goes too long over their lease.  If they are
     * overdue it closes it out from under them and notifies a waiting thread
     * they can allocate a new one
     */
    public class Slumlord extends Thread
    {

        public Slumlord()
        {
            super();
            setDaemon(true);
        }

        public void run()
        {
            while (true)
            {
                try
                {
                    sleep(mEvictionFrequency);
                }
                catch (InterruptedException e)
                {
                }

                synchronized (mAvailable)
                {
                    long currentTime = System.currentTimeMillis();
                    Iterator leases = mLeases.iterator();
                    while (leases.hasNext())
                    {
                        Lease lease = (Lease) leases.next();
                        if (lease.getExpiration() < currentTime)
                        {
                            leases.remove();
                            System.out.println("The SLUMLORD is most DISPLEASED - no more squatting for " + lease.getResource() + ".");
                            try
                            {
                                releaseResource(lease.getResource());
                            }
                            catch (ResourceManagerException e)
                            {
                                logException(e);
                            }

                            mAvailable.notify();
                        }
                    }
                }
            }
        }
    }

    /**
     * Subclasses must implement this method in order
     * to return new instances of the resource
     */
    public abstract Object getNewResource() throws ResourceManagerException;

    /**
     * Subclasses must implement this method in order
     * to help this class tell whether a resource is
     * still alive.
     */
    public abstract boolean isAlive(Object obj) throws ResourceManagerException;

    /**
     * Subclasses must implement this method in order
     * for the manager to clean up after itself. If
     * there is nothing to do with the resources,
     * this can contain a blank implementation.
     */
    public abstract void releaseResource(Object obj) throws ResourceManagerException;

	public abstract void logException(ResourceManagerException e);	
}