package org.matsim.contrib.emissions.analysis;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.analysis.spatial.Grid;
import org.matsim.contrib.analysis.spatial.HexagonalGrid;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.contrib.analysis.spatial.SquareGrid;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.contrib.emissions.types.Pollutant;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EmissionGridAnalyzer {

    private final double binSize;
    private final double smoothingRadius;

    private static GeometryFactory factory = new GeometryFactory();
    private final GridType gridType;
    private final double gridSize;
    private final Network network;

    private EmissionGridAnalyzer(final double binSize, final double gridSize, final double smoothingRadius,
                                 final GridType gridType, final Network network) {
        this.binSize = binSize;
        this.network = network;
        this.gridType = gridType;
        this.gridSize = gridSize;
        this.smoothingRadius = smoothingRadius;
    }

    public TimeBinMap<Grid<Map<Pollutant, Double>>> process(String eventsFile) {

        TimeBinMap<Map<Id<Link>, LinkEmissions>> timeBinsWithEmissions = processEventsFile(eventsFile);
        TimeBinMap<Grid<Map<Pollutant, Double>>> result = new TimeBinMap<>(binSize);

        timeBinsWithEmissions.getAllTimeBins().forEach(bin -> {
            Grid<Map<Pollutant, Double>> grid = writeAllLinksToGrid(bin.getValue());
            result.getTimeBin(bin.getStartTime()).setValue(grid);
        });
        return result;
    }

    private TimeBinMap<Map<Id<Link>, LinkEmissions>> processEventsFile(String eventsFile) {

        EventsManager eventsManager = EventsUtils.createEventsManager();
        EmissionEventsReader eventsReader = new EmissionEventsReader(eventsManager);
        EmissionsOnLinkEventHandler handler = new EmissionsOnLinkEventHandler(binSize);
        eventsManager.addHandler(handler);
        eventsReader.readFile(eventsFile);
        return handler.getTimeBins();
    }

    private void processLink(Link link, LinkEmissions emissions, Grid<Map<Pollutant, Double>> grid) {

        grid.getValues().forEach(cell -> {
            double weight = SpatialInterpolation.calculateWeightFromLine(
                    transformToCoordinate(link.getFromNode()), transformToCoordinate(link.getToNode()),
                    cell.getCoordinate(), smoothingRadius);
            processCell(cell, emissions, weight);
        });
    }

    private Grid<Map<Pollutant, Double>> createGrid() {

        Geometry bounds = getBoundsFromNetwork();
        if (gridType == GridType.Hexagonal)
            return new HexagonalGrid<>(gridSize, HashMap::new, bounds);
        else
            return new SquareGrid<>(gridSize, HashMap::new, bounds);
    }

    private Grid<Map<Pollutant, Double>> writeAllLinksToGrid(Map<Id<Link>, LinkEmissions> linksWithEmissions) {

        Grid<Map<Pollutant, Double>> grid = createGrid();

        linksWithEmissions.forEach((id, emission) -> {
            Link link = network.getLinks().get(id);
            processLink(link, emission, grid);
        });
        return grid;
    }

    public enum GridType {Square, Hexagonal}

    private void processCell(Grid.Cell<Map<Pollutant, Double>> cell, LinkEmissions emissions, double weight) {

        // merge both maps from cell and linkemissions and sum up values, while the link emissions are multiplied by
        // the cell weight
        Map<Pollutant, Double> newValues = Stream.concat(cell.getValue().entrySet().stream(), emissions.getEmissions().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (cellValue, linkValue) -> cellValue + linkValue * weight));
        cell.setValue(newValues);
    }

    public static class Builder {
        private double binSize;
        private double gridSize;
        private double smoothingRadius = 1.0;
        private Network network;
        private GridType gridType = GridType.Square;

        public Builder withTimeBinSize(double size) {
            this.binSize = size;
            return this;
        }

        public Builder withGridType(GridType type) {
            this.gridType = type;
            return this;
        }

        public Builder withGridSize(double size) {
            gridSize = size;
            return this;
        }

        public Builder withSmoothingRadius(double radius) {
            smoothingRadius = radius;
            return this;
        }

        public Builder withNetwork(Network network) {
            this.network = network;
            return this;
        }

        public EmissionGridAnalyzer build() {

            if (!isValid())
                throw new IllegalArgumentException("binSize, gridSize, smoothingRadius must be set and greater 0, Also network must be set");

            return new EmissionGridAnalyzer(binSize, gridSize, smoothingRadius, gridType, network);
        }

        private boolean isValid() {
            return binSize > 0 && gridSize > 0 && smoothingRadius > 0 && network != null;
        }
    }

    private Geometry getBoundsFromNetwork() {
        double[] box = NetworkUtils.getBoundingBox(network.getNodes().values());
        return factory.createPolygon(new Coordinate[]{
                new Coordinate(box[0], box[1]),
                new Coordinate(box[2], box[1]),
                new Coordinate(box[2], box[3]),
                new Coordinate(box[0], box[3]),
                new Coordinate(box[0], box[1])});
    }

    private Coordinate transformToCoordinate(Node node) {
        return new Coordinate(node.getCoord().getX(), node.getCoord().getY());
    }
}
