/*
 * Copyright (C) 2020 Grakn Labs
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
 *
 */

package grakn.core.concept.type.impl;

import grakn.core.concept.type.Rule;
import grakn.core.graph.GraphManager;
import grakn.core.graph.vertex.RuleVertex;
import graql.lang.pattern.Pattern;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static grakn.core.graph.util.Encoding.Edge.Rule.CONCLUSION;
import static grakn.core.graph.util.Encoding.Edge.Rule.CONDITION_NEGATIVE;
import static grakn.core.graph.util.Encoding.Edge.Rule.CONDITION_POSITIVE;

public class RuleImpl implements Rule {

    private final GraphManager graphMgr;
    private final RuleVertex vertex;

    private RuleImpl(final GraphManager graphMgr, final RuleVertex vertex) {
        this.graphMgr = graphMgr;
        this.vertex = vertex;
    }

    private RuleImpl(final GraphManager graphMgr, final String label, final Pattern when, final Pattern then) {
        this.graphMgr = graphMgr;
        this.vertex = graphMgr.schema().create(label, when, then);
        putPositiveConditions();
        putNegativeConditions();
        putConclusions();
    }

    public static RuleImpl of(final GraphManager graphMgr, final RuleVertex vertex) {
        return new RuleImpl(graphMgr, vertex);
    }

    public static RuleImpl of(final GraphManager graphMgr, final String label, final Pattern when, final Pattern then) {
        return new RuleImpl(graphMgr, label, when, then);
    }

    private void putPositiveConditions() {

        // TODO extract when Types from when pattern
        final List<String> whenTypesPositive = Arrays.asList();

        vertex.outs().delete(CONDITION_POSITIVE);

        whenTypesPositive.forEach(label -> {
            vertex.outs().put(CONDITION_POSITIVE, graphMgr.schema().getRule(label));
        });
    }

    private void putNegativeConditions() {

        // TODO extract when Types from when pattern
        final List<String> whenTypesNegative = Arrays.asList();

        vertex.outs().delete(CONDITION_NEGATIVE);

        whenTypesNegative.forEach(label -> {
            vertex.outs().put(CONDITION_NEGATIVE, graphMgr.schema().getRule(label));
        });
    }

    private void putConclusions() {

        // TODO extract when Types from then pattern
        final List<String> conclusionTypes = Arrays.asList();

        vertex.outs().delete(CONCLUSION);

        conclusionTypes.forEach(label -> {
            vertex.outs().put(CONCLUSION, graphMgr.schema().getRule(label));
        });
    }

    @Override
    public String getLabel() {
        return vertex.label();
    }

    @Override
    public void setLabel(final String label) {
        vertex.label(label);
    }

    @Override
    public Pattern getWhen() {
        return vertex.when();
    }

    @Override
    public Pattern getThen() {
        return vertex.then();
    }

    @Override
    public void setWhen(final Pattern when) {
        vertex.when(when);
    }

    @Override
    public void setThen(final Pattern then) {
        vertex.then(then);
    }

    @Override
    public Stream<TypeImpl> positiveConditionTypes() {
        return vertex.outs().edge(CONDITION_POSITIVE).to().map(v -> TypeImpl.of(graphMgr, v)).stream();
    }

    @Override
    public Stream<TypeImpl> negativeConditionTypes() {
        return vertex.outs().edge(CONDITION_NEGATIVE).to().map(v -> TypeImpl.of(graphMgr, v)).stream();
    }

    @Override
    public Stream<TypeImpl> conclusionTypes() {
        return vertex.outs().edge(CONCLUSION).to().map(v -> TypeImpl.of(graphMgr, v)).stream();
    }

    @Override
    public boolean isDeleted() {
        return vertex.isDeleted();
    }

    @Override
    public void delete() {
        vertex.delete();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        final RuleImpl that = (RuleImpl) object;
        return this.vertex.equals(that.vertex);
    }

    @Override
    public final int hashCode() {
        return vertex.hashCode(); // does not need caching
    }
}
