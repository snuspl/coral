# Vortex 
[![Build Status](http://cmscluster.snu.ac.kr:8080/jenkins/buildStatus/icon?job=Vortex-master)](http://cmscluster.snu.ac.kr:8080/jenkins/job/Vortex-master/)

## Requirements
* Java 8
* Maven
* Protobuf 2.5.0

## Installing Vortex
* Run all tests and install: `mvn clean install -T 2C`
* Run only unit tests and install: `mvn clean install -DskipITs -T 2C`

## Examples
```bash
./bin/run.sh -job_id mr_none -user_main edu.snu.vortex.examples.beam.MapReduce -optimization_policy none -user_args "`pwd`/src/main/resources/sample_input_mr `pwd`/src/main/resources/sample_output"
./bin/run.sh -job_id mr_pado -user_main edu.snu.vortex.examples.beam.MapReduce -optimization_policy pado -user_args "`pwd`/src/main/resources/sample_input_mr `pwd`/src/main/resources/sample_output"
./bin/run.sh -job_id mr_disaggr -user_main edu.snu.vortex.examples.beam.MapReduce -optimization_policy disaggregation -user_args "`pwd`/src/main/resources/sample_input_mr `pwd`/src/main/resources/sample_output"
./bin/run.sh -job_id mr_runtime_opt -user_main edu.snu.vortex.examples.beam.MapReduce -optimization_policy runtime_opt -user_args "`pwd`/src/main/resources/sample_input_mr `pwd`/src/main/resources/sample_output"
./bin/run.sh -job_id broadcast_pado -user_main edu.snu.vortex.examples.beam.Broadcast -optimization_policy pado -user_args "`pwd`/src/main/resources/sample_input_mr `pwd`/src/main/resources/sample_output"
./bin/run.sh -job_id als_pado -user_main edu.snu.vortex.examples.beam.AlternatingLeastSquare -optimization_policy pado -user_args "`pwd`/src/main/resources/sample_input_als 10 3"
./bin/run.sh -job_id mlr_pado -user_main edu.snu.vortex.examples.beam.MultinomialLogisticRegression -optimization_policy pado -user_args "`pwd`/src/main/resources/sample_input_mlr 100 5 3"
java -cp target/vortex-0.1-SNAPSHOT-shaded.jar edu.snu.vortex.compiler.optimizer.examples.MapReduce

// yarn cluster example
./bin/run.sh -deploy_mode yarn -job_id mr_pado -user_main edu.snu.vortex.examples.beam.MapReduce -optimization_policy pado -user_args "hdfs://maas-14:9000/sample_input_mr hdfs://maas-14:9000/sample_output_mr"
```

## DAG Visualization
Vortex Compiler and Engine stores JSON representation of intermediate DAGs.
`-dag_dir` option specifies the directory to store JSON files. By default JSON files are saved in `./target/dag`.

```bash
# Example for specifying target directory for JSON representation of DAGs.
./bin/run.sh -user_main edu.snu.vortex.examples.beam.AlternatingLeastSquare -optimization_policy pado -dag_dir "./target/dag/als" -user_args "`pwd`/src/main/resources/sample_input_als 10 3"
```

You can easily visualize a DAG using [online visualizer](https://service.jangho.kr/vortex-dag/) with the corresponding JSON file.

## Instructions for installing Protobuf
* Vortex uses v2.5.0 downloadable at: https://github.com/google/protobuf/releases/tag/v2.5.0
* Extract the downloaded tarball and command:
    - sudo ./configure
    - sudo make
    - sudo make check
    - sudo make install
* To check for a successful installation of v2.5.0: protoc --version
