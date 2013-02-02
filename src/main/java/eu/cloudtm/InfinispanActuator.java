package eu.cloudtm;

import eu.cloudtm.exception.ComponentNotFoundException;
import eu.cloudtm.exception.ConnectionException;
import eu.cloudtm.exception.InvocationException;
import eu.cloudtm.exception.NoJmxProtocolRegisterException;
import eu.cloudtm.jmxprotocol.JmxProtocol;
import eu.cloudtm.jmxprotocol.JmxRMIProtocol;
import eu.cloudtm.jmxprotocol.RemotingJmxProtocol;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import java.io.IOException;
import java.util.*;

/**
 * This class allows to the programmer to invoke JMX methods in single/any/multiple {@link InfinispanMachine}.
 * <br/>
 * It allows to add your own JMX protocol. By default, the RMI and the JBoss' remoting-jmx is already registered.
 * <br/>
 *
 * @author Pedro Ruivo
 * @since 1.0
 */
public class InfinispanActuator {

    /**
     * represents an invocation error. used in
     * {@link #invokeInAllMachines(String, String, String, String, Object[], String[])}
     */
    public static final Object ERROR_INVOKING = new Object();
    /**
     * an array to use when the method to invoke has no parameters. using this array is not mandatory but it will
     * avoid creating every time a new array
     */
    public static final Object[] EMPTY_PARAMETER = new Object[0];
    /**
     * an array to use when the method to invoke has no parameters. using this array is not mandatory but it will
     * avoid creating every time a new array
     */
    public static final String[] EMPTY_SIGNATURE = new String[0];
    private static final InfinispanActuator INFINISPAN_ACTUATOR = new InfinispanActuator();
    private final List<JmxProtocol> jmxProtocols;
    private final Set<InfinispanMachine> infinispanMachines;

    public InfinispanActuator() {
        this.jmxProtocols = new LinkedList<JmxProtocol>();
        this.infinispanMachines = new HashSet<InfinispanMachine>();
        registerJmxProtocol(new JmxRMIProtocol());
        registerJmxProtocol(new RemotingJmxProtocol());
    }

    /**
     * @return this instance
     */
    public static InfinispanActuator getInstance() {
        return INFINISPAN_ACTUATOR;
    }

    /**
     * it register a new {@link JmxProtocol}. By default, the RMI and the remoting-jmx protocols are already registered
     *
     * @param protocol the JMX protocol
     */
    public final void registerJmxProtocol(JmxProtocol protocol) {
        if (protocol == null) {
            return;
        }
        synchronized (jmxProtocols) {
            jmxProtocols.add(protocol);
        }
    }

    /**
     * adds a new {@link InfinispanMachine} to the machine list.
     *
     * @param machine the new machine
     */
    public final void addMachine(InfinispanMachine machine) {
        synchronized (infinispanMachines) {
            infinispanMachines.add(machine);
        }
    }

    /**
     * removes the machine with the {@param hostname} and {@param port} from the available machines list.
     *
     * @param hostname the hostname
     * @param port     the port (String representation)
     */
    public final void removeMachine(String hostname, String port) {
        if (port == null) {
            return;
        }
        synchronized (infinispanMachines) {
            for (Iterator<InfinispanMachine> iterator = infinispanMachines.iterator(); iterator.hasNext(); ) {
                InfinispanMachine machine = iterator.next();
                if (machine.getHostname().equals(hostname) && machine.getPort().equals(port)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * removes all the machines with the {@param hostname} independent of the port
     *
     * @param hostname the hostname
     */
    public final void removeMachines(String hostname) {
        synchronized (infinispanMachines) {
            for (Iterator<InfinispanMachine> iterator = infinispanMachines.iterator(); iterator.hasNext(); ) {
                InfinispanMachine machine = iterator.next();
                if (machine.getHostname().equals(hostname)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * triggers the data placement optimizer.
     *
     * @param infinispanDomain the Infinispan JMX domain, it is like registered in Infinispan configuration file
     * @param cacheName        the cache name
     * @throws NoJmxProtocolRegisterException if no Jmx Protocol is registered
     * @throws InvocationException            if the method was not invoked successfully in any
     *                                        {@link InfinispanMachine} registered.
     */
    public final void triggerDataPlacement(String infinispanDomain, String cacheName)
            throws NoJmxProtocolRegisterException, InvocationException {
        invokeOnceInAnyMachine(infinispanDomain, cacheName, "DataPlacementManager", "dataPlacementRequest",
                EMPTY_PARAMETER, EMPTY_SIGNATURE);
    }

    /**
     * triggers the switch mechanism to a new replication protocol
     *
     * @param infinispanDomain the Infinispan JMX domain, it is like registered in Infinispan configuration file
     * @param cacheName        the cache name
     * @param protocolId       the new replication protocol ID
     * @param forceStop        true if you want to force the use of stop-the-world model
     * @param abortOnStop      (only for stop-the-world model) if true, it aborts the running transactions instead
     *                         of waiting for them to finish
     * @throws NoJmxProtocolRegisterException if no JMX Protocol is registered
     * @throws InvocationException            if the method was not invoked successfully in any
     *                                        {@link InfinispanMachine} registered.
     */
    public final void triggerSwitchReplicationProtocol(String infinispanDomain, String cacheName, String protocolId,
                                                       boolean forceStop, boolean abortOnStop)
            throws NoJmxProtocolRegisterException, InvocationException {
        invokeOnceInAnyMachine(infinispanDomain, cacheName, "ReconfigurableReplicationManager",
                "switchTo", new Object[]{protocolId, forceStop, abortOnStop},
                new String[]{"String", "boolean", "boolean"});
    }

    /**
     * triggers the replication degree mechanism to change it to a new replication degree
     *
     * @param infinispanDomain  the Infinispan JMX domain, it is like registered in Infinispan configuration file
     * @param cacheName         the cache name
     * @param replicationDegree the new replication degree
     * @throws NoJmxProtocolRegisterException if no JMX Protocol is registered
     * @throws InvocationException            if the method was not invoked successfully in any
     *                                        {@link InfinispanMachine} registered.
     */
    public final void triggerNewReplicationDegree(String infinispanDomain, String cacheName, int replicationDegree)
            throws NoJmxProtocolRegisterException, InvocationException {
        invokeOnceInAnyMachine(infinispanDomain, cacheName, "DataPlacementManager",
                "setReplicationDegree", new Object[]{replicationDegree},
                new String[]{"int"});
    }

    /**
     * Generic JMX invocation method, that is only invoked in the specific {@param machine}
     *
     * @param machine          the machine to be invoked
     * @param infinispanDomain the Infinispan JMX domain, it is like registered in Infinispan configuration file
     * @param cacheName        the cache name
     * @param componentName    the component name
     * @param methodName       the method name
     * @param parameter        the method's parameters
     * @param signature        the method's signature
     * @return the value returned by the method invocation
     * @throws NoJmxProtocolRegisterException if no JMX Protocol is registered
     * @throws ConnectionException            if the connection ti the {@param machine} was not successfully made by
     *                                        any of the JMX protocols
     * @throws ComponentNotFoundException     if the component specified was not found
     * @throws InvocationException            if the method was not invoked successfully
     */
    public final Object invokeInMachine(InfinispanMachine machine, String infinispanDomain, String cacheName,
                                        String componentName, String methodName, Object[] parameter,
                                        String[] signature) throws NoJmxProtocolRegisterException, ConnectionException,
            ComponentNotFoundException, InvocationException {
        try {
            MBeanServerConnection connection = createConnection(machine);
            ObjectName objectName = findCacheComponent(connection, infinispanDomain, cacheName, componentName);
            return connection.invoke(objectName, methodName, parameter, signature);
        } catch (ReflectionException e) {
            throw new InvocationException(e);
        } catch (MBeanException e) {
            throw new InvocationException(e);
        } catch (InstanceNotFoundException e) {
            throw new ComponentNotFoundException(e);
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    /**
     * Generic JMX invocation that tries to invoke the method in one of the machines registered, ensuring that the
     * method is invoked at least once (or it throws an exception)
     *
     * @param infinispanDomain Infinispan JMX domain, like it is registered in Infinispan configuration file
     * @param cacheName        the cache name
     * @param componentName    the component name
     * @param methodName       the method name
     * @param parameter        the method's parameters
     * @param signature        the method's signature
     * @return the value returned by the method invocation
     * @throws NoJmxProtocolRegisterException if no JMX protocols are registered
     * @throws InvocationException            if the method was not invoked successfully by any machine registered
     */
    public final Object invokeOnceInAnyMachine(String infinispanDomain, String cacheName, String componentName,
                                               String methodName, Object[] parameter, String[] signature)
            throws NoJmxProtocolRegisterException, InvocationException {
        MBeanServerConnection connection;
        ObjectName objectName;
        synchronized (infinispanMachines) {
            for (InfinispanMachine machine : infinispanMachines) {
                try {
                    connection = createConnection(machine);
                    objectName = findCacheComponent(connection, infinispanDomain, cacheName, componentName);
                    return connection.invoke(objectName, methodName, parameter, signature);
                } catch (Exception e) {
                    //no-op
                }
            }
        }
        throw new InvocationException("An error occurs while trying to invoke " + componentName + "." + methodName);
    }

    /**
     * Generic JMX invocation, that invokes the method in all the machines.
     *
     * @param infinispanDomain the Infinispan JMX domain, like it is registered in Infinispan configuration file
     * @param cacheName        the cache name
     * @param componentName    the component name
     * @param methodName       the method name
     * @param parameter        the method's parameters
     * @param signature        the method's signature
     * @return a map between the {@link InfinispanMachine} and the returned value. If the invocation fails for some
     *         machine, them the value is equals to {@link #ERROR_INVOKING}
     * @throws NoJmxProtocolRegisterException if no JMX protocols are registered
     */
    public final Map<InfinispanMachine, Object> invokeInAllMachines(String infinispanDomain, String cacheName,
                                                                    String componentName, String methodName,
                                                                    Object[] parameter, String[] signature)
            throws NoJmxProtocolRegisterException {
        Map<InfinispanMachine, Object> results = new HashMap<InfinispanMachine, Object>();
        MBeanServerConnection connection;
        ObjectName objectName;

        synchronized (infinispanMachines) {
            for (InfinispanMachine machine : infinispanMachines) {
                try {
                    connection = createConnection(machine);
                    objectName = findCacheComponent(connection, infinispanDomain, cacheName, componentName);
                    Object result = connection.invoke(objectName, methodName, parameter, signature);
                    results.put(machine, result);
                } catch (Exception e) {
                    results.put(machine, ERROR_INVOKING);
                }
            }
        }
        return results;
    }

    /**
     * creates a MBean Server Connection to the machine represented by {@param InfinispanMachine}
     *
     * @param machine the machine
     * @return the {@link MBeanServerConnection} to that machine
     * @throws NoJmxProtocolRegisterException if no JMX protocols are registered
     * @throws ConnectionException            if it cannot connect via any JMX protocol registered
     */
    private MBeanServerConnection createConnection(InfinispanMachine machine) throws NoJmxProtocolRegisterException,
            ConnectionException {
        MBeanServerConnection mBeanServerConnection = null;
        synchronized (jmxProtocols) {
            if (jmxProtocols.isEmpty()) {
                throw new NoJmxProtocolRegisterException();
            }

            Map<String, Object> environment = new HashMap<String, Object>();
            if (machine.getUsername() != null && !machine.getUsername().isEmpty()) {
                environment.put(JMXConnector.CREDENTIALS, new String[]{machine.getUsername(), machine.getPassword()});
            }

            JMXConnector jmxConnector;

            for (JmxProtocol jmxProtocol : jmxProtocols) {
                try {
                    jmxConnector = JMXConnectorFactory.connect(jmxProtocol.createUrl(machine.getHostname(),
                            machine.getPort()), environment);
                } catch (IOException e) {
                    //no luck
                    continue;
                }
                try {
                    mBeanServerConnection = jmxConnector.getMBeanServerConnection();
                    break;
                } catch (IOException e) {
                    //no luck
                }
            }
        }
        if (mBeanServerConnection == null) {
            throw new ConnectionException("Cannot Connect to " + machine);
        }
        return mBeanServerConnection;
    }

    /**
     * tries to find the cache component defined by {@param infinispanDomain}, {@param cacheName} and {@param component}
     *
     * @param connection       the {@link MBeanServerConnection} in which it tries to find the {@link ObjectName}
     * @param infinispanDomain the Infinispan JMX domain, like it is registered in Infinispan configuration file
     * @param cacheName        the cache name
     * @param component        the component name
     * @return the {@link ObjectName} that represents the component
     * @throws ComponentNotFoundException if the component was not found in this connection
     * @throws ConnectionException        if a connection error occurs while trying to find the component
     */
    private ObjectName findCacheComponent(MBeanServerConnection connection, String infinispanDomain, String cacheName,
                                          String component) throws ComponentNotFoundException, ConnectionException {
        try {
            for (ObjectName name : connection.queryNames(null, null)) {
                if (name.getDomain().equals(infinispanDomain)) {

                    if ("Cache".equals(name.getKeyProperty("type")) && cacheName.equals(name.getKeyProperty("name")) &&
                            component.equals(name.getKeyProperty("component"))) {
                        return name;
                    }
                }
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
        throw new ComponentNotFoundException(infinispanDomain, cacheName, component);
    }
}
