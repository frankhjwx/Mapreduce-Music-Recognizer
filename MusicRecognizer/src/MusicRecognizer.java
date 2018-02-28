import hash.CombineHash;
import hash.FFT;
import hash.Hash;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.LongWritable;
import pcm.PCM16MonoData;
import pcm.PCM16MonoParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.TreeSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.lib.input.LineRecordReader;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import sun.security.krb5.Config;

public class MusicRecognizer {
    public static String systempath = "hdfs://master01:9000/user/2017st25/";

    public static class FactorLoaderMap extends Mapper<Object,Text,Text,Text>{
        private Text map_value = new Text();
        private Text map_key = new Text();
        private ArrayList<Hash> hashes = new ArrayList<Hash>();

        public void setup(Context context) throws IOException{
            Configuration jobconf = context.getConfiguration();
            String uri = systempath + "MusicRec/questdata/" + jobconf.get("questFile");
            Configuration conf = new Configuration();
            FileSystem fs = FileSystem.get(URI.create(uri), conf);
            FSDataInputStream in = null;
            in = fs.open(new Path(uri));
            hashes = CombineHash.generateFingerprint(in, 0);
        }

        public void map(Object key, Text value,Context context)
                throws IOException, InterruptedException {
            String[] args = value.toString().split("\t");
            String[] factors = args[1].split("@");
            for (Hash targethash:hashes) {
                if (Integer.valueOf(args[0]) == targethash.getHashID()) {
                    //context.write(new Text("HASH#"+String.valueOf(targethash.getHashID())+"#"+String.valueOf(targethash.offset)), new Text("1"));
                    for (String factor:factors){
                        String[] s = factor.split("#");
                        context.write(new Text(s[2]+"#"+s[1]+"#"+String.valueOf((
                                        Integer.valueOf(s[0]) - targethash.offset) / 5)),
                                new Text("1"));
                    }
                }
            }
        }
    }

    public static class FactorLoaderCombiner extends Reducer<Text, Text, Text, Text> {
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            Integer cnt = 0;
            for (Text value : values) {
                cnt += Integer.valueOf(value.toString());
            }
            String[] s = key.toString().split("#");
            context.write(new Text(s[0] + "#" + s[1]), new Text(String.valueOf(cnt)));
        }
    }

    public static class FactorLoaderReduce extends Reducer<Text,Text,Text,Text>{
        private Text result = new Text();

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            Integer max = -1;
            for (Text value : values) {
                if (Integer.valueOf(value.toString())>max)
                    max = Integer.valueOf(value.toString());
            }
            context.write(key, new Text(String.valueOf(max)));
        }
    }

    public static class GradeCombinerMap extends Mapper<Object,Text,Text,Text> {
        private Text map_value = new Text();
        private Text map_key = new Text();

        public void map(Object key, Text value,Context context)
                throws IOException, InterruptedException {
            String[] s = value.toString().split("\t");
            context.write(new Text("1"), new Text(s[0] + "@" + s[1]));
        }
    }

    public static class GradeCombinerReduce extends Reducer<Text,Text,Text,Text> {
        private Text result = new Text();

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            int[] maxgrade = {-1,-1,-1,-1,-1};
            String[] maxsong = {"","","","",""};
            for (Text value : values){
                String[] args = value.toString().split("@");
                args[0] = args[0].split("#")[1];
                for (int i=0; i<5; i++)
                    if (Integer.valueOf(args[1]) > maxgrade[i]) {
                        for (int j=4; j>i; j--) {
                            maxgrade[j] = maxgrade[j-1];
                            maxsong[j] = maxsong[j-1];
                        }
                        maxgrade[i] = Integer.valueOf(args[1]);
                        maxsong[i] = args[0];
                        break;
                    }
            }
            for (int i=0; i<5; i++)
                context.write(new Text(maxsong[i]), new Text(String.valueOf(maxgrade[i])));
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {

        Configuration conf1 = new Configuration();
        conf1.set("questFile", args[0]);

        Job job1 = new Job(conf1,"FactorLoader");

        job1.setJarByClass(MusicRecognizer.class);


        job1.setMapperClass(FactorLoaderMap.class);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(Text.class);

        job1.setCombinerClass(FactorLoaderCombiner.class);

        job1.setReducerClass(FactorLoaderReduce.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job1, new Path(systempath + "MusicRec/FactorData"));
        FileOutputFormat.setOutputPath(job1, new Path(systempath + "MusicRec/MusicRecognizertmp"));
        job1.waitForCompletion(true);

        Configuration conf2 = new Configuration();

        Job job2 = new Job(conf2,"GradeCombiner");

        job2.setJarByClass(FactorExtractor.class);

        job2.setMapperClass(GradeCombinerMap.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(Text.class);

        job2.setReducerClass(GradeCombinerReduce.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job2, new Path(systempath + "MusicRec/MusicRecognizertmp"));
        FileOutputFormat.setOutputPath(job2, new Path(systempath + "MusicRec/output"));
        job2.waitForCompletion(true);

        System.exit(job1.waitForCompletion(true)?0:1);

    }
}
