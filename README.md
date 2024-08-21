# M-Health Application with Seven-Segment Digit Recognition Feature for Reading Blood Pressure Values on Digital Sphygmomanometer
Blood pressure is a crucial metric in health assessments as it reflects an individual’s general health status and could help the diagnosis of cardiovascular diseases, which are the leading cause of death worldwide. Currently, blood pressure data recording is still performed manually, making it prone to human error and inefficiency. Therefore, there is a need for a solution that enables accurate, fast, and practical reading and recording of blood pressure measurement data. This paper proposes the development of a mobile health (m-health) application prototype equipped with 4 features: blood pressure measurement reading using deep learning, data visualization, user management, and authentication. The model was trained using 3,649 images of sphygmomanometer with objective to recognize seven-segment digits representing 3 blood pressure metrics. Various YOLOv8 model variants—small, medium, and large—were utilized in the training. Each variant also underwent model compression techniques such as quantization and pruning. The evaluation result indicated that the small variant of the YOLOv8 model, that quantized to INT8, proved to be the most suitable model. This is attributed to its compact size (11 MB) and short inference time (641.4 ms). The model has achieved a seven-segment digit detection accuracy of 99.28% and an f1-score of 96.48%. The model was successfully deployed in the m-health application, with a slight increase in average inference time to 1867.6 ms. Furthermore, direct testing of the model on 40 images within the m-health application yielded a seven-segment digit grouping accuracy of 96.67% and an overall image reading accuracy of 95%.

## Documentation
[View Documentation](./13520046-Hansel%20Valentino%20Tanoto-Paper.pdf)

## User Interface
![User Interface](./mhealth_ui.png)

## Detection Result's Sample
![Detection Result](./mhealth_detection_result_sample.png)
