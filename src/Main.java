////////////////////////////////////// NICCOLO' CASELLI 7115264 //////////////////////////////////////////////////////////////
// Errori:
// La sintatti è tutta coretta ad eccezine di due sviste: ho  dimenticato il tipo di ritorno del metodo getSize() della classe InputQueue
// e ho creato un array di ArrayList di int e non di Integer.
// Per quanto riguarda la logica ho dimenticato di incremenetare completed_count nel ProcessorThread.
// Per quanto rigurda il main ho dimenticato lo sleep, quindi ovviamente il programma terminava subito.
// Ho inoltre interrotto e fatto il join per generatori e i processori ma ho dimenticato  il printer.
// La sincronizzazione funziona.
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


import java.util.ArrayList;
import java.util.Arrays;

class InputQueueResponse {
    public int [] values;
    public int extractionId;

    public InputQueueResponse(int id, int[] values) {
        this.values = values;
        this.extractionId = id;
    }
}

class Counter {
    private int count = 0;
    public synchronized int getId() {
        int old = count;
        count++;
        return old;
    }
}

class ProcessorResult extends InputQueueResponse{
    public int result;

    public ProcessorResult(int result, int extractionId, int[] values) {
        super(extractionId, values); // <-- non serve il this.super
        this.result = result;
    }
}

class InputQueue {
    private final ArrayList<Integer>[] queue; // <--- array list di Integer e non int
    private int N, L;
    private final Counter counter;

    public InputQueue(int N, int L, Counter counter) {
        this.N = N;
        this.L = L;
        this.queue = new ArrayList[N];

        for (int i = 0; i < N; i++)
            queue[i] = new ArrayList<Integer>();

        this.counter = counter;
    }

    public synchronized void put(int id, int value) throws InterruptedException {
        while (queue[id].size() >= L) {
            wait();
        }
        queue[id].add(value);
        notifyAll();
    }

    public synchronized InputQueueResponse extract() throws InterruptedException {
        while (!isReady()) {
            wait();
        }

        int[] values = new int[N];
        for (int i = 0; i < N; i++) {
            values[i] = queue[i].remove(0);
        }

        int extractionId = counter.getId();
        InputQueueResponse res = new InputQueueResponse(extractionId, values);
        notifyAll();
        return res;
    }

    private boolean isReady() {
       boolean ready = true;
        for (int i = 0; i < N; i++) {
           if (this.queue[i].size() == 0) {
               ready = false;
               break;
           }
        }
        return ready;
    }

    public int getSize() { // <-- mi sono dimentcato del tipo di ritorno
        int n = 0;
        for (int i = 0; i < N; i++) {
            n += queue[i].size();
            System.out.println("Queue 1_" + i + " size: " + queue[i].size()); // TODO: remove
        }
        return n;
    }
}


class UnlimitedQueue {
       ArrayList<ProcessorResult> data;

       public UnlimitedQueue() {
           this.data = new ArrayList<ProcessorResult>();
       }

       public synchronized void put(ProcessorResult res) {
           data.add(res);
           notifyAll();
       }

       public synchronized ProcessorResult extract(int id) throws InterruptedException {
           ProcessorResult found;
              while ((found = findResult(id)) == null) {
                wait();
              }
              data.remove(found);
              return found;
       }

       public int getSize() {
           return data.size();
       }

       private ProcessorResult findResult(int id) {
           for (int i = 0; i < data.size(); i++) {
               if (data.get(i) != null && data.get(i).extractionId == id) {
                   return data.get(i);
               }
           }
           return null;
       }
}

class Generator extends Thread {
    int x, id, produced_count, count;
    InputQueue input;

    public Generator(int x, int id, InputQueue input) {
        this.x = x;
        this.id = id;
        this.produced_count = 0;
        this.count = 0;
        this.input = input;
    }

    @Override
    public void run() {
       try {
           while (true) {
               int value = (id +1)+ count;
               count++;
               input.put(id, value);
               produced_count++;
               sleep(x);
           }
       }
       catch (InterruptedException e) {
           System.out.println("Generator " + id + " interrupted");
       }
    }

    public int printStats() {
        System.out.println("Generator " + id + " produced: " + produced_count);
        return produced_count;
    }
}

class ProcessorThread extends Thread{
    InputQueue input;
    UnlimitedQueue output;
    public int T, D, completed_count;

    public ProcessorThread(InputQueue input, UnlimitedQueue output, int T, int D) {
        this.input = input;
        this.output = output;
        this.completed_count = 0;
        this.T = T;
        this.D = D;
    }

    @Override
    public void run() {
        try {
            while (true) {
                InputQueueResponse payload = input.extract();
                sleep(T+(int)(Math.random() * D));
                int res = 0;
                for (int i = 0; i < payload.values.length; i++) {
                    res += payload.values[i];
                }

                ProcessorResult result = new ProcessorResult(res, payload.extractionId, payload.values);

                System.out.println(getName() + " is working on: " + Arrays.toString(payload.values)); // TODO: remove

                output.put(result);

                System.out.println(getName() + " processed: " + Arrays.toString(payload.values) + " result: " + res); // TODO: remove

                completed_count++;
            }
        }
        catch (InterruptedException e) {
            System.out.println("Processor interrupted");
        }
    }
}

class PrinterThread extends Thread {
    public int count = 0;
    UnlimitedQueue output;

    public PrinterThread(UnlimitedQueue output) {
        this.output = output;
    }

    @Override
    public void run() {
        try {
            while (true) {
                ProcessorResult result = output.extract(count);
                System.out.println("Extraction=" + result.extractionId + ", values=" + Arrays.toString(result.values) + ", result=" + result.result);
                count++;
            }
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted Printer");
        }
    }
}


public class Main {
    public static void main(String[] args) throws  InterruptedException {
        Counter counter = new Counter();
        int N = 4;
        int M = 3;
        int L = 10;
        InputQueue input = new InputQueue(N, L, counter);
        UnlimitedQueue output = new UnlimitedQueue();
        Generator[] generators = new Generator[N];
        ProcessorThread[] processors = new ProcessorThread[M];
        PrinterThread printer = new PrinterThread(output);


        for (int i = 0; i < N; i++) {
            generators[i] = new Generator(500, i, input);
            generators[i].start();
        }

        for (int i = 0; i < M; i++) {
            processors[i] = new ProcessorThread(input, output, 1000, 1000);
            processors[i].start();
            processors[i].setName("Processor " + i); // TODO: remove
        }

        printer.start();

        Thread.sleep(10000); // <-- mi sono dimenticato di fare lo sleep

        for (int i = 0; i < N; i++) generators[i].interrupt();
        for (int i = 0; i < M; i++) processors[i].interrupt();
        for (int i = 0; i < N; i++) generators[i].join();
        for (int i = 0; i < M; i++) processors[i].join();
        // mi sono dimenticato di fare il join per il printer
        printer.interrupt();
        printer.join();

        int total_generated = 0;
        for (int i = 0; i < N; i++) {
            total_generated += generators[i].printStats();
        }
        System.out.println("Total generated: " + total_generated);

        int total_processed = 0;
        for (int i = 0; i < M; i++) {
            total_processed += processors[i].completed_count;
            System.out.println("Processor " + i + " processed: " + processors[i].completed_count);
        }

        System.out.println("Total processed: " + total_processed);
        System.out.println("Total printed: " + printer.count);
        System.out.println("In queue 1: " + input.getSize());
        System.out.println("In queue 2: " + output.getSize());

    }
}