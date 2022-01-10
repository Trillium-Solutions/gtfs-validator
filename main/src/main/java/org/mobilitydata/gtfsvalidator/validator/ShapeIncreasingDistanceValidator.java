/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mobilitydata.gtfsvalidator.validator;

import static org.mobilitydata.gtfsvalidator.util.S2Earth.getDistanceMeters;

import com.google.common.collect.Multimaps;
import java.util.List;
import javax.inject.Inject;
import org.mobilitydata.gtfsvalidator.annotation.GtfsValidator;
import org.mobilitydata.gtfsvalidator.notice.NoticeContainer;
import org.mobilitydata.gtfsvalidator.notice.SeverityLevel;
import org.mobilitydata.gtfsvalidator.notice.ValidationNotice;
import org.mobilitydata.gtfsvalidator.table.GtfsShape;
import org.mobilitydata.gtfsvalidator.table.GtfsShapeTableContainer;

/**
 * Validates that shape_dist_traveled along a shape in "shapes.txt" are increasing.
 *
 * <p>Generated notice:
 *
 * <ul>
 *   <li>{@link DecreasingShapeDistanceNotice}
 *   <li>{@link EqualShapeDistanceSameCoordinatesNotice}
 *   <li>{@link EqualShapeDistanceDiffCoordinatesNotice}
 * </ul>
 */
@GtfsValidator
public class ShapeIncreasingDistanceValidator extends FileValidator {

  private static final float MAX_DISTANCE_SHAPEPOINTS_METERS = 1.11f;
  private final GtfsShapeTableContainer table;

  @Inject
  ShapeIncreasingDistanceValidator(GtfsShapeTableContainer table) {
    this.table = table;
  }

  @Override
  public void validate(NoticeContainer noticeContainer) {
    for (List<GtfsShape> shapeList : Multimaps.asMap(table.byShapeIdMap()).values()) {
      // GtfsShape objects are sorted based on @SequenceKey annotation on shape_pt_sequence field.
      for (int i = 1; i < shapeList.size(); ++i) {
        GtfsShape prev = shapeList.get(i - 1);
        GtfsShape curr = shapeList.get(i);
        if (!(prev.hasShapeDistTraveled() || curr.hasShapeDistTraveled())) {
          continue;
        }
        if (prev.shapeDistTraveled() > curr.shapeDistTraveled()) {
          noticeContainer.addValidationNotice(new DecreasingShapeDistanceNotice(prev, curr));
          continue;
        }
        if (prev.shapeDistTraveled() != curr.shapeDistTraveled()) {
          continue;
        }
        // equal shape_dist_traveled and different coordinates
        if (curr.shapePtLat() != prev.shapePtLat() || curr.shapePtLon() != prev.shapePtLon()) {
          // check if shape points are more than 1.11 meters away
          if (MAX_DISTANCE_SHAPEPOINTS_METERS
              < getDistanceMeters(curr.shapePtLatLon(), prev.shapePtLatLon())) {
            noticeContainer.addValidationNotice(
                new EqualShapeDistanceDiffCoordinatesNotice(prev, curr));
          }
        } else if (curr.shapePtLat() == prev.shapePtLat()
            && curr.shapePtLon() == prev.shapePtLon()) {
          // equal shape_dist_traveled and same coordinates
          noticeContainer.addValidationNotice(
              new EqualShapeDistanceSameCoordinatesNotice(prev, curr));
        }
      }
    }
  }

  /**
   * Checks if two {@code GtfsShape} have different coordinates and are farther than {@code
   * ShapeIncreasingDistanceValidator#MAX_DISTANCE_SHAPEPOINTS}.
   *
   * @param shape the first {@code GtfsShape}
   * @param otherShape the other {@code GtfsShape}
   * @return true the two {@code GtfsShape} have distinct coordinates and if the distance that
   *     separates them is less or equal than {@code
   *     ShapeIncreasingDistanceValidator#MAX_DISTANCE_SHAPEPOINTS}; false otherwise.
   */
  private static boolean areFarWithDifferentCoordinates(GtfsShape shape, GtfsShape otherShape) {
    return shape.shapePtLon() != otherShape.shapePtLon()
        && shape.shapePtLat() != otherShape.shapePtLat()
        && MAX_DISTANCE_SHAPEPOINTS_METERS
            < getDistanceMeters(shape.shapePtLatLon(), otherShape.shapePtLatLon());
  }

  /**
   * When sorted on {@code shapes.shape_pt_sequence} key, shape points must have strictly increasing
   * values for `shapes.shape_dist_traveled`
   *
   * <p>"Values must increase along with shape_pt_sequence."
   * (http://gtfs.org/reference/static/#shapestxt)
   *
   * <p>Severity: {@code SeverityLevel.ERROR}
   */
  static class DecreasingShapeDistanceNotice extends ValidationNotice {
    private final String shapeId;
    private final long csvRowNumber;
    private final double shapeDistTraveled;
    private final int shapePtSequence;
    private final long prevCsvRowNumber;
    private final double prevShapeDistTraveled;
    private final int prevShapePtSequence;

    DecreasingShapeDistanceNotice(GtfsShape current, GtfsShape previous) {
      super(SeverityLevel.ERROR);
      this.shapeId = current.shapeId();
      this.csvRowNumber = current.csvRowNumber();
      this.shapeDistTraveled = current.shapeDistTraveled();
      this.shapePtSequence = current.shapePtSequence();
      this.prevCsvRowNumber = previous.csvRowNumber();
      this.prevShapeDistTraveled = previous.shapeDistTraveled();
      this.prevShapePtSequence = previous.shapePtSequence();
    }
  }

  /**
   * When sorted on {@code shapes.shape_pt_sequence} key, close shape points should not have equal
   * values for {@code shapes.shape_dist_traveled}
   *
   * <p>"Values must increase along with shape_pt_sequence."
   * (http://gtfs.org/reference/static/#shapestxt)
   *
   * <p>Severity: {@code SeverityLevel.WARNING}
   */
  static class EqualShapeDistanceSameCoordinatesNotice extends ValidationNotice {
    private final String shapeId;
    private final long csvRowNumber;
    private final double shapeDistTraveled;
    private final int shapePtSequence;
    private final long prevCsvRowNumber;
    private final double prevShapeDistTraveled;
    private final int prevShapePtSequence;

    EqualShapeDistanceSameCoordinatesNotice(GtfsShape previous, GtfsShape current) {
      super(SeverityLevel.WARNING);
      this.shapeId = current.shapeId();
      this.csvRowNumber = current.csvRowNumber();
      this.shapeDistTraveled = current.shapeDistTraveled();
      this.shapePtSequence = current.shapePtSequence();
      this.prevCsvRowNumber = previous.csvRowNumber();
      this.prevShapeDistTraveled = previous.shapeDistTraveled();
      this.prevShapePtSequence = previous.shapePtSequence();
    }
  }

  /**
   * When sorted on {@code shapes.shape_pt_sequence} key, distant shape points must not have equal
   * values for {@code shapes.shape_dist_traveled}
   *
   * <p>"Values must increase along with shape_pt_sequence."
   * (http://gtfs.org/reference/static/#shapestxt)
   *
   * <p>Severity: {@code SeverityLevel.ERROR}
   */
  static class EqualShapeDistanceDiffCoordinatesNotice extends ValidationNotice {
    private final String shapeId;
    private final long csvRowNumber;
    private final double shapeDistTraveled;
    private final int shapePtSequence;
    private final long prevCsvRowNumber;
    private final double prevShapeDistTraveled;
    private final int prevShapePtSequence;

    EqualShapeDistanceDiffCoordinatesNotice(GtfsShape previous, GtfsShape current) {
      super(SeverityLevel.ERROR);
      this.shapeId = current.shapeId();
      this.csvRowNumber = current.csvRowNumber();
      this.shapeDistTraveled = current.shapeDistTraveled();
      this.shapePtSequence = current.shapePtSequence();
      this.prevCsvRowNumber = previous.csvRowNumber();
      this.prevShapeDistTraveled = previous.shapeDistTraveled();
      this.prevShapePtSequence = previous.shapePtSequence();
    }
  }
}
