# QC dashboard:
## Model:
- one aligned feature list
- list of raw files from the feature list. use a list to set specific order
- selected row
- AbundanceMeasure
- Sample type from metadata (list/set)
- Batch grouping metadata column
- Raw file -> color

## Controls in the dashboard:
- how to select the row in the dashboard? full feature table probably too big due to graphical types. Maybe create a feature table fx and disable all of the graphical types and the raw data file specific Types. Maybe just show Row ID, mz, RT and preferred annotation columns
- Combo box for AbundanceMeasure to define which abundance type is used for all plots
- CheckCombo box with sample type metadata column to optionally select different sample types? Default: only QC
- group raw files in feature list potentially by a metadata column that can contain an acquisition batch (String or Number column, gorup with metadata utils)
    - select metadata column via MetadataGroupingComponent
    - if no metadata is selected, all in one group
    - set up a mapping from raw file -> color for this dashboard
        - if batch is give one color to each batch and use shading in the color utils for the files
        - if no batch is selected, use rawdatafile.getColor (awt version)
- for all plots: if raw data files on x axis, sort by acquisition date (make sure the list in the model is sorted)

## Plots
### response-based plots, most important
- Plot 1: plot intensity for all samples for single rowas a scatter plot. range: intensity, x: data file (number is fine, add data file name as tooltip)
    - only really usable for features that have the same concentration in all data files. for example internal standards.
    - maybe add SD/RSD intervals
    - QCs and samples as different datasets so they have a different color
    - ![img.png](img.png)
    - does it make sense to also do a plot like this with the median feature intensity across files?
    - ![img_1.png](img_1.png)

- Plot 2: scatter plot of number of detected features across the selected files
    - y axis: number of features for data file, x axis: data file - as before numbers is fine, have the same index as other plots
    - ![img_2.png](img_2.png)

- Plot 3: scatter plot of summed intensity of features
    - y axis: feature sum intensity, x: raw file
    - ![img_3.png](img_3.png)

- Plot 4: Count how often a feature was detected in all QCs, sort descending, scatterplot (or histogram)
    - in case of scatter plot: x axis: row index in sorted list, y axis: number of detections
    - in case of histogram: x axis: number of detections, y: number of features
    - good quality (for the dataset): >50% of all MS1 features detected in all QCs - in scatter plot, add ValueMarker on x axis
    - Warwick recommendation: if a feature was detected in more than 70% is good, otherwise discard - in scatter plot: add value marker on x axis
    - ![img_4.png](img_4.png)

### instrumental
- Plot 5 and 6: mass deviation, rt deviation
    - x axis: raw file, y axis: mass/rt deviation of selected feature (absolute) vs the average mz/rt of the row
    - ![img_5.png](img_5.png)

## other
- does pump pressure correlate across all datasets?
- Mentioned: Hoteling T² in pca scores plot to detect outlieders, like a confidence interval. does it make sense to add?

Maybe as e filter: Blank contribution ( blank area/qc area * 100 or so, check, warwick dunne) (i think if blank intensity > 5% of qc intensity, remove)
If rsd < 0.1% in qcs, remove, too good to be true



## Example dashboard:

![img_6.png](img_6.png)
Powerpoint: "D:\OneDrive - mzio GmbH\mzio\development\features\QC\qc dashboard.pptx"