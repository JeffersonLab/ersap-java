/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgRegQuery;
import org.jlab.coda.xmsg.data.xMsgRegRecord;

/**
 * The standard filters to select ERSAP DPEs, containers or services.
 */
public final class ErsapFilters {

    private ErsapFilters() { }

    /**
     * Returns a filter to select all DPEs in the ERSAP cloud.
     * The filter will select every running DPE, of any language.
     *
     * @return a filter for all DPEs
     */
    public static DpeFilter allDpes() {
        return dpes();
    }


    /**
     * Returns a filter to select all containers in the ERSAP cloud.
     * The filter will select every deployed container, in every DPE, of any language.
     *
     * @return a filter for all containers
     */
    public static ContainerFilter allContainers() {
        return containers();
    }


    /**
     * Returns a filter to select all services in the ERSAP cloud.
     * The filter will select every deployed service, in every container of every DPE,
     * of any language.
     *
     * @return a filter for all services
     */
    public static ServiceFilter allServices() {
        return services();
    }


    /**
     * Returns a filter to select a specific DPE.
     * <p>
     * Example: the DPE {@code 10.2.9.100_java}.
     *
     * @param name the selected DPE
     * @return a filter for a single DPE
     */
    static DpeFilter dpe(DpeName name) {
        xMsgTopic topic = xMsgTopic.build("dpe", name.canonicalName());
        return new DpeFilter(xMsgRegQuery.subscribers().withSame(topic));
    }


    /**
     * Returns a filter to select all the DPEs of the given host.
     * A host can contain multiple DPEs of different languages.
     * The filter will select all DPEs running in the specified host.
     * <p>
     * Example: all the DPEs on the host {@code 10.2.9.100}.
     *
     * @param host the selected host
     * @return a filter for all DPEs in the host
     */
    public static DpeFilter dpesByHost(String host) {
        return dpes(host);
    }


    /**
     * Returns a filter to select all the DPEs of the given language.
     * The filter will select every running DPE of the specified language.
     *
     * @param lang the language to filter
     * @return a filter for all DPEs of the given language
     */
    public static DpeFilter dpesByLanguage(ErsapLang lang) {
        DpeFilter filter = dpes();
        filter.addRegFilter(r -> ErsapUtil.getDpeLang(r.name()).equals(lang.toString()));
        return filter;
    }


    /**
     * Returns a filter to select a specific container.
     * <p>
     * Example: the container {@code 10.2.9.100_java:master}.
     *
     * @param name the selected container
     * @return a filter for a single container
     */
    static ContainerFilter container(ContainerName name) {
        xMsgTopic topic = xMsgTopic.build("container", name.canonicalName());
        ContainerFilter filter = new ContainerFilter(xMsgRegQuery.subscribers().withSame(topic));
        filter.addFilter(o -> o.getString("name").equals(name.toString()));
        return filter;
    }


    /**
     * Returns a filter to select all the containers of the given host.
     * A host can contain multiple DPEs of different languages.
     * The filter will select all containers deployed on every DPE running
     * in the specified host.
     * <p>
     * Example: all the containers on the host {@code 10.2.9.100}.
     *
     * @param host the selected host
     * @return a filter for all containers in the host
     */
    public static ContainerFilter containersByHost(String host) {
        return containers(host);
    }


    /**
     * Returns a filter to select all the containers of the given DPE.
     * A host can contain multiple DPEs of different languages.
     * The filter will select every container deployed on the specified DPE.
     * <p>
     * Example: all the containers on the DPE {@code 10.2.9.100_cpp}.
     *
     * @param dpeName the selected DPE
     * @return a filter for all containers in the DPE
     */
    public static ContainerFilter containersByDpe(DpeName dpeName) {
        ContainerFilter filter = containers(dpeName.address().host());
        filter.addRegFilter(r -> ErsapUtil.getDpeName(r.name()).equals(dpeName.toString()));
        return filter;
    }


    /**
     * Returns a filter to select all the containers of the given language.
     * The filter will select all containers deployed on every running DPE of
     * the specified language.
     * <p>
     * Example: all the {@code java} containers.
     *
     * @param lang the language to filter
     * @return a filter for all containers of the given language
     */
    public static ContainerFilter containersByLanguage(ErsapLang lang) {
        ContainerFilter filter = containers();
        filter.addRegFilter(r -> ErsapUtil.getDpeLang(r.name()).equals(lang.toString()));
        return filter;
    }


    /**
     * Returns a filter to select all the containers of the given name.
     * The filter will select every container deployed on any running DPE whose
     * name matches the specified name. The match must be exact.
     * <p>
     * Example: all containers named {@code master}.
     *
     * @param name the container name to filter
     * @return a filter for all containers with the given name
     */
    public static ContainerFilter containersByName(String name) {
        ContainerFilter filter = containers();
        filter.addRegFilter(r -> ErsapUtil.getContainerName(r.name()).equals(name));
        filter.addFilter(o -> ErsapUtil.getContainerName(o.getString("name")).equals(name));
        return filter;
    }


    /**
     * Returns a filter to select a specific service.
     * <p>
     * Example: the container {@code 10.2.9.100_java:master:SqrRoot}.
     *
     * @param name the selected service
     * @return a filter for a single service
     */
    static ServiceFilter service(ServiceName name) {
        xMsgTopic topic = xMsgTopic.wrap(name.canonicalName());
        ServiceFilter filter = new ServiceFilter(xMsgRegQuery.subscribers().withSame(topic));
        filter.addFilter(o -> o.getString("name").equals(name.toString()));
        return filter;
    }


    /**
     * Returns a filter to select all the services of the given host.
     * A host can contain multiple DPEs of different languages.
     * The filter will select all services deployed on every DPE running
     * in the specified host.
     * <p>
     * Example: all the services on the host {@code 10.2.9.100}.
     *
     * @param host the selected host
     * @return a filter for all services in the host
     */
    public static ServiceFilter servicesByHost(String host) {
        return services(host);
    }


    /**
     * Returns a filter to select all the services of the given DPE.
     * A host can contain multiple DPEs of different languages.
     * The filter will select every service deployed on the specified DPE.
     * <p>
     * Example: all the services on the DPE {@code 10.2.9.100_cpp}.
     *
     * @param dpeName the selected DPE
     * @return a filter for all services in the DPE
     */
    public static ServiceFilter servicesByDpe(DpeName dpeName) {
        ServiceFilter filter = services(dpeName.address().host());
        filter.addRegFilter(r -> ErsapUtil.getDpeName(r.name()).equals(dpeName.toString()));
        return filter;
    }


    /**
     * Returns a filter to select all the services of the given container.
     * The filter will select every service deployed on the specified container.
     * <p>
     * Example: all the services on the container {@code 10.2.9.100_cpp:master}.
     *
     * @param containerName the selected container
     * @return a filter for all services in the container
     */
    public static ServiceFilter servicesByContainer(ContainerName containerName) {
        String container = containerName.toString();
        ServiceFilter filter = services(containerName.address().host());
        filter.addRegFilter(r -> {
            return ErsapUtil.getContainerCanonicalName(r.name()).equals(container);
        });
        filter.addFilter(o -> {
            return ErsapUtil.getContainerCanonicalName(o.getString("name")).equals(container);
        });
        return filter;
    }


    /**
     * Returns a filter to select all the services of the given language.
     * The filter will select all services deployed on every running DPE of
     * the specified language.
     * <p>
     * Example: all the {@code java} services.
     *
     * @param lang the language to filter
     * @return a filter for all services of the given language
     */
    public static ServiceFilter servicesByLanguage(ErsapLang lang) {
        ServiceFilter filter = services();
        filter.addRegFilter(r -> ErsapUtil.getDpeLang(r.name()).equals(lang.toString()));
        return filter;
    }


    /**
     * Returns a filter to select all the services of the given name.
     * The filter will select every service deployed on any running DPE whose
     * engine matches the specified name. The match must be exact.
     * <p>
     * Example: all services named {@code SqrRoot}.
     *
     * @param name the engine name to filter
     * @return a filter for all services of the given name
     */
    public static ServiceFilter servicesByName(String name) {
        ServiceFilter filter = services();
        filter.addRegFilter(r -> ErsapUtil.getEngineName(r.name()).equals(name));
        filter.addFilter(o -> ErsapUtil.getEngineName(o.getString("name")).equals(name));
        return filter;
    }


    /**
     * Returns a filter to select all the services of the given author.
     * The filter will select every service deployed on any running DPE whose
     * author matches the specified name. The match must be exact.
     * <p>
     * Example: all services developed by {@code John Doe}.
     *
     * @param authorName the author name to filter
     * @return a filter for all services by the given author
     */
    public static ServiceFilter servicesByAuthor(String authorName) {
        ServiceFilter filter = services();
        filter.addFilter(o -> o.getString("author").equals(authorName));
        return filter;
    }


    /**
     * Returns a filter to select all the services of the given description.
     * The filter will select every service deployed on any running DPE whose
     * description matches the specified regular expression.
     * <p>
     * Example: all services with the regex in its description.
     *
     * @param regex the engine name to filter
     * @return a filter for all services by a matching description
     */
    public static ServiceFilter servicesByDescription(String regex) {
        ServiceFilter filter = services();
        filter.addFilter(o -> o.getString("description").matches(regex));
        return filter;
    }


    private static DpeFilter dpes() {
        return new DpeFilter(xMsgRegQuery.subscribers().withDomain("dpe"));
    }


    private static DpeFilter dpes(String host) {
        DpeFilter filter = new DpeFilter(xMsgRegQuery.subscribers().withHost(host));
        filter.addRegFilter(r -> r.topic().domain().equals("dpe"));
        return filter;
    }


    private static ContainerFilter containers() {
        return new ContainerFilter(xMsgRegQuery.subscribers().withDomain("container"));
    }


    private static ContainerFilter containers(String host) {
        ContainerFilter filter = new ContainerFilter(xMsgRegQuery.subscribers().withHost(host));
        filter.addRegFilter(r -> r.topic().domain().equals("container"));
        return filter;
    }


    private static ServiceFilter services() {
        ServiceFilter filter = new ServiceFilter(xMsgRegQuery.subscribers().all());
        filter.addRegFilter(ErsapFilters::isService);
        return filter;
    }


    private static ServiceFilter services(String host) {
        ServiceFilter filter = new ServiceFilter(xMsgRegQuery.subscribers().withHost(host));
        filter.addRegFilter(ErsapFilters::isService);
        return filter;
    }


    private static boolean isService(xMsgRegRecord record) {
        String domain = record.topic().domain();
        return !domain.equals("dpe") && !domain.equals("container");
    }
}
