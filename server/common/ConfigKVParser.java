/*
 * Copyright (C) 2021 Vaticle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.vaticle.typedb.core.server.common;

import com.vaticle.typedb.common.yaml.Yaml;
import com.vaticle.typedb.core.common.collection.Bytes;
import com.vaticle.typedb.core.common.exception.TypeDBException;
import com.vaticle.typedb.core.server.common.CommandLine.Option;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vaticle.typedb.common.util.Objects.className;
import static com.vaticle.typedb.core.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static com.vaticle.typedb.core.common.exception.ErrorMessage.Server.CONFIG_ENUM_UNEXPECTED_VALUE;
import static com.vaticle.typedb.core.common.exception.ErrorMessage.Server.CONFIG_UNEXPECTED_VALUE;
import static com.vaticle.typedb.core.common.exception.ErrorMessage.Server.MISSING_CONFIG_OPTION;
import static com.vaticle.typedb.core.common.iterator.Iterators.iterate;
import static com.vaticle.typedb.core.server.common.Util.scopeKey;

public abstract class ConfigKVParser implements Option.CliHelp {

    private final String description;

    ConfigKVParser(String description) {
        this.description = description;
    }

    String description() {
        return description;
    }

    static class MapParser<TYPE> extends ConfigKVParser {

        private final ValueParser<TYPE> valueParser;

        private MapParser(String description, ValueParser<TYPE> valueParser) {
            super(description);
            this.valueParser = valueParser;
        }

        static <TYPE> MapParser<TYPE> create(String description, ValueParser<TYPE> valueParser) {
            return new MapParser<>(description, valueParser);
        }

        Map<String, TYPE> parseFrom(Yaml.Map yaml, String scope, EntryParser<?>... exclude) {
            Set<String> excludeKeys = iterate(exclude).map(EntryParser::key).toSet();
            Map<String, TYPE> read = new HashMap<>();
            yaml.forEach((key, value) -> {
                if (!excludeKeys.contains(key)) {
                    String scopedKey = scopeKey(scope, key);
                    read.put(key, valueParser.parse(value, scopedKey));
                }
            });
            return read;
        }

        @Override
        public Help help(String optionScope) {
            if (valueParser.isLeaf()) {
                return new Help.Leaf(scopeKey(optionScope, "<name>"), description(), valueParser.asLeaf().help());
            } else {
                return new Help.Section(scopeKey(optionScope, "<name>"), description(), valueParser.asNested().help(scopeKey(optionScope, "<name>")));
            }
        }
    }

    static abstract class EntryParser<TYPE> extends ConfigKVParser {

        private final String key;
        final ValueParser<TYPE> valueParser;

        EntryParser(String key, String description, ValueParser<TYPE> valueParser) {
            super(description);
            this.key = key;
            this.valueParser = valueParser;
        }

        public String key() {
            return key;
        }

        abstract TYPE parse(Yaml.Map yaml, String scope);

        static class Value<TYPE> extends EntryParser<TYPE> {

            private Value(String key, String description, ValueParser<TYPE> valueParser) {
                super(key, description, valueParser);
            }

            static <TYPE> Value<TYPE> create(String key, String description, ValueParser<TYPE> valueType) {
                return new Value<>(key, description, valueType);
            }

            @Override
            TYPE parse(Yaml.Map yaml, String scope) {
                String scopedKey = scopeKey(scope, key());
                if (!yaml.containsKey(key())) throw TypeDBException.of(MISSING_CONFIG_OPTION, scopedKey);
                else return valueParser.parse(yaml.get(key()), scopedKey);
            }

            @Override
            public Help help(String optionScope) {
                String scopedKey = scopeKey(optionScope, key());
                if (valueParser.isLeaf()) {
                    return new Help.Leaf(scopedKey, description(), valueParser.asLeaf().help());
                } else {
                    return new Help.Section(scopedKey, description(), valueParser.asNested().help(scopedKey));
                }
            }
        }

        static class EnumValue<TYPE> extends EntryParser<TYPE> {

            private final List<TYPE> values;

            private EnumValue(String key, List<TYPE> values, ValueParser<TYPE> valueParser, String description) {
                super(key, description, valueParser);
                this.values = values;
            }

            static <T> EnumValue<T> create(String key, String description, ValueParser<T> valueParser, List<T> values) {
                return new EnumValue<>(key, values, valueParser, description);
            }

            @Override
            TYPE parse(Yaml.Map yaml, String scope) {
                String scopedKey = scopeKey(scope, key());
                if (!yaml.containsKey(key())) throw TypeDBException.of(MISSING_CONFIG_OPTION, scopedKey);
                else {
                    TYPE value = valueParser.parse(yaml.get(key()), scopedKey);
                    if (values.contains(value)) return value;
                    else throw TypeDBException.of(CONFIG_ENUM_UNEXPECTED_VALUE, scopedKey, value, values);
                }
            }

            @Override
            public Help help(String optionScope) {
                String scopedKey = scopeKey(optionScope, key());
                if (valueParser.isLeaf()) {
                    String values = String.join("|", iterate(this.values).map(Object::toString).toList());
                    return new Help.Leaf(scopedKey, description(), values);
                } else {
                    return new Help.Section(scopedKey, description(), valueParser.asNested().help(scopedKey));
                }
            }
        }
    }

    public static abstract class ValueParser<TYPE> {

        abstract TYPE parse(Yaml yaml, String scope);

        boolean isLeaf() {
            return false;
        }

        Leaf<TYPE> asLeaf() {
            throw TypeDBException.of(ILLEGAL_CAST, className(getClass()), className(Leaf.class));
        }

        boolean isNested() {
            return false;
        }

        Nested<TYPE> asNested() {
            throw TypeDBException.of(ILLEGAL_CAST, className(getClass()), className(Nested.class));
        }

        static abstract class Nested<T> extends ValueParser<T> {

            abstract List<Help> help(String scope);

            @Override
            boolean isNested() {
                return true;
            }

            @Override
            Nested<T> asNested() {
                return this;
            }
        }

        public static class Leaf<T> extends ValueParser<T> {
            public static final Leaf<String> STRING = new Leaf<>(
                    (yaml) -> yaml.isString(),
                    (yaml) -> yaml.asString().value(),
                    "<string>"
            );
            static final Leaf<Integer> INTEGER = new Leaf<>(
                    (yaml) -> yaml.isInt(),
                    (yaml) -> yaml.asInt().value(),
                    "<int>"
            );
            static final Leaf<Float> FLOAT = new Leaf<>(
                    (yaml) -> yaml.isFloat(),
                    (yaml) -> yaml.asFloat().value(),
                    "<float>"
            );
            static final Leaf<Boolean> BOOLEAN = new Leaf<>(
                    (yaml) -> yaml.isBoolean(),
                    (yaml) -> yaml.asBoolean().value(),
                    "<boolean>"
            );
            public static final Leaf<Path> PATH = new Leaf<>(
                    (yaml) -> yaml.isString(),
                    (yaml) -> Paths.get(yaml.asString().value()),
                    "<relative or absolute path>"
            );
            public static final Leaf<Long> BYTES_SIZE = new Leaf<>(
                    (yaml) -> yaml.isString() && BytesParser.isValidSizeString(yaml.asString().value()),
                    (yaml) -> BytesParser.parse(yaml.asString().value()),
                    "<size>"
            );
            public static final Leaf<InetSocketAddress> INET_SOCKET_ADDRESS = new Leaf<>(
                    (yaml) -> {
                        if (!yaml.isString()) return false;
                        // use URI to parse IPV4, IVP4 and host names - however, we must add a dummy scheme to use it
                        URI uri = URI.create("scheme://" + yaml.asString().value());
                        return uri.getHost() != null && uri.getPort() != -1;
                    },
                    (yaml) -> {
                        URI uri = URI.create("scheme://" + yaml.asString().value());
                        return new InetSocketAddress(uri.getHost(), uri.getPort());
                    },
                    "<address:port>"
            );
            static final Leaf<List<String>> LIST_STRING = new Leaf<>(
                    (yaml) -> yaml.isList() && iterate(yaml.asList().iterator()).allMatch(Yaml::isString),
                    (yaml) -> iterate(yaml.asList().iterator()).map(elem -> elem.asString().value()).toList(),
                    "<[string, ...]>");

            private final Function<Yaml, Boolean> validator;
            private final Function<Yaml, T> converter;
            private final String help;

            private Leaf(Function<Yaml, Boolean> validator, Function<Yaml, T> converter, String help) {
                this.validator = validator;
                this.converter = converter;
                this.help = help;
            }

            @Override
            T parse(Yaml yaml, String scope) {
                if (!validator.apply(yaml)) {
                    throw TypeDBException.of(CONFIG_UNEXPECTED_VALUE, scope, yaml, help);
                } else {
                    return converter.apply(yaml);
                }
            }

            @Override
            boolean isLeaf() {
                return true;
            }

            @Override
            Leaf<T> asLeaf() {
                return this;
            }

            public String help() {
                return help;
            }
        }

        /**
         * Derived from logback FileSize implementation
         */
        private static class BytesParser {

            private final static String LENGTH_PART = "([0-9]+)";
            private final static int LENGTH_GROUP = 1;
            private final static String UNIT_PART = "(|kb|mb|gb)?";
            private final static int UNIT_GROUP = 2;
            private static final Pattern FILE_SIZE_PATTERN = Pattern.compile(LENGTH_PART + "\\s*" + UNIT_PART, Pattern.CASE_INSENSITIVE);

            private static long parse(String size) {
                Matcher matcher = FILE_SIZE_PATTERN.matcher(size);
                long coefficient;
                if (matcher.matches()) {
                    String lenStr = matcher.group(LENGTH_GROUP);
                    String unitStr = matcher.group(UNIT_GROUP);
                    long lenValue = Long.parseLong(lenStr);
                    if (unitStr.equalsIgnoreCase("")) coefficient = 1;
                    else if (unitStr.equalsIgnoreCase("kb")) coefficient = Bytes.KB;
                    else if (unitStr.equalsIgnoreCase("mb")) coefficient = Bytes.MB;
                    else if (unitStr.equalsIgnoreCase("gb")) coefficient = Bytes.GB;
                    else throw new IllegalStateException("Unexpected size unit: " + unitStr);
                    return lenValue * coefficient;
                } else {
                    throw new IllegalArgumentException("Size [" + size + "] is not in a recognised format.");
                }
            }

            private static boolean isValidSizeString(String size) {
                Matcher matcher = FILE_SIZE_PATTERN.matcher(size);
                return matcher.matches();
            }
        }
    }
}
