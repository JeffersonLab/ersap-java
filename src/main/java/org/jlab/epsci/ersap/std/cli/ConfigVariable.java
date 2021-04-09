/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jline.reader.Completer;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;

/**
 * A configuration variable for a ERSAP shell session.
 */
public final class ConfigVariable {

    private final String name;
    private final String description;

    private final Function<String[], Object> parser;
    private final Completer completer;

    private Object currentValue;


    /**
     * Creates a new builder of a configuration variable.
     *
     * @param name the name of the variable
     * @param description the description of the value stored in the variable
     * @return the builder
     */
    public static Builder newBuilder(String name, String description) {
        return new Builder(name, description);
    }


    /**
     * Helps creating a new {@link ConfigVariable}.
     */
    public static final class Builder {

        private String name;
        private String description;

        private Function<String[], Object> parser;
        private Completer completer;

        private volatile Object initialValue;

        private Builder(String name, String description) {
            this.name = name;
            this.description = description;
            this.parser = ConfigParsers::toString;
            this.completer = NullCompleter.INSTANCE;
        }

        /**
         * Sets the function to transform the shell arguments into the value
         * stored in the variable.
         * <p>
         * Given a variable {@code v}, the following expression must be true:
         * <pre>
         * parser.apply(new String[]{v.getValue().toString()}).equals(v.getValue())
         * </pre>
         *
         * @param parser a function to parse the value from shell arguments
         * @return this builder
         */
        public Builder withParser(Function<String[], Object> parser) {
            this.parser = parser;
            return this;
        }

        /**
         * Sets the default value for the variable.
         * When the shell configuration is reset, the variable will be set to
         * this value.
         *
         * @param value the default value
         * @return this builder
         */
        public Builder withInitialValue(Object value) {
            Objects.requireNonNull(value, "null initial value");
            this.initialValue = value;
            return this;
        }

        /**
         * Sets the expected values for the variable.
         * The shell will present them as tab-completion candidates when setting
         * the variable to a new value.
         *
         * @param values the expected values of the variable
         * @return this builder
         */
        public Builder withExpectedValues(Object... values) {
            String[] strings = Stream.of(values).map(Object::toString).toArray(String[]::new);
            this.completer = new StringsCompleter(strings);
            return this;
        }

        /**
         * Sets the shell completer to show the tab-completion candidates when
         * setting the variable to a new value.
         *
         * @param completer the shell completer for the possible variable values
         * @return this builder
         */
        public Builder withCompleter(Completer completer) {
            this.completer = completer;
            return this;
        }

        /**
         * Creates the variable.
         *
         * @return the new variable.
         */
        public ConfigVariable build() {
            return new ConfigVariable(this);
        }
    }


    private ConfigVariable(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parser = builder.parser;
        this.completer = builder.completer;
        this.currentValue = builder.initialValue;
    }

    /**
     * Gets the name of this variable.
     *
     * @return the name of the variable
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the description of this variable.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this variable has a value.
     *
     * @return true if this variable has a value, false otherwise
     */
    public boolean hasValue() {
        return currentValue != null;
    }

    /**
     * Gets the current value of this variable.
     *
     * @return the value of the variable
     * @throws IllegalStateException if the variable is not set
     */
    public Object getValue() {
        if (currentValue == null) {
            throw new IllegalStateException("config variable '" + name + "' not set");
        }
        return currentValue;
    }

    /**
     * Sets this variable with a new value.
     *
     * @param value the new value for the variable
     */
    public void setValue(Object value) {
        Objects.requireNonNull(value, "null value for config variable '" + name + "'");
        currentValue = value;
    }

    void parseValue(String... args) {
        setValue(parser.apply(args));
    }

    Completer getCompleter() {
        return completer;
    }
}
