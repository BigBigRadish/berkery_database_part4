package simpledb;


import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;


/**
 * Each instance of HeapPage stores data for one page of HeapFiles and
 * implements the Page interface that is used by BufferPool.
 *
 * @see HeapFile
 * @see BufferPool
 */
public class HeapPage implements Page {

    private HeapPageId pid;
    private TupleDesc td;
    private byte header[];
    private Tuple tuples[];
    private int numSlots;
    private TransactionId lastDirtyOperation;

    // oldData�����ü�setBeforeImage()��getBeforeImage()����
    // ���һ��HeapPage���޸�ǰ����setBeforeImage(),���ܽ���ǰ�����ݱ�������
    // ���޸ĺ�ͨ��getBeforeImage()����޸�ǰ��HeapPage
    byte[] oldData;

    /**
     * Create a HeapPage from a set of bytes of data read from disk.
     * The format of a HeapPage is a set of header bytes indicating
     * the slots of the page that are in use, some number of tuple slots.
     * Specifically, the number of tuples is equal to: <p>
     * floor((BufferPool.PAGE_SIZE*8) / (tuple size * 8 + 1))
     * <p> where tuple size is the size of tuples in this
     * database table, which can be determined via {@link Catalog#getTupleDesc}.
     * The number of 8-bit header words is equal to:
     * <p>
     * ceiling(no. tuple slots / 8)
     * <p>
     *
     * @see Database#getCatalog
     * @see Catalog#getTupleDesc
     * @see BufferPool#PAGE_SIZE
     */
    public HeapPage(HeapPageId id, byte[] data) throws IOException {
        this.pid = id;
        this.td = Database.getCatalog().getTupleDesc(id.getTableId());
        this.numSlots = getNumTuples();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        // allocate and read the header slots of this page
        header = new byte[getHeaderSize()];
        for (int i = 0; i < header.length; i++)
            header[i] = dis.readByte();

        try {
            // allocate and read the actual records of this page
            tuples = new Tuple[numSlots];
            for (int i = 0; i < tuples.length; i++)
                tuples[i] = readNextTuple(dis, i);
        } catch (NoSuchElementException e) {
            e.printStackTrace();
        }
        dis.close();

        setBeforeImage();
    }

    /**
     * Retrieve the number of tuples on this page.
     *
     * @return the number of tuples on this page
     */
    private int getNumTuples() {
        // some code goes here
        if (numSlots != 0) {
            return numSlots;
        }
        //int�����������������ȡ����
        numSlots = (BufferPool.PAGE_SIZE * 8) / (td.getSize() * 8 + 1);
        return numSlots;

    }

    /**
     * Computes the number of bytes in the header of a page in a HeapFile with each tuple occupying tupleSize bytes
     *
     * @return the number of bytes in the header of a page in a HeapFile with each tuple occupying tupleSize bytes
     */
    private int getHeaderSize() {
        // some code goes here
        return (int) Math.ceil(getNumTuples() / 8.0);

    }

    /**
     * Return a view of this page before it was modified
     * -- used by recovery
     */
    public HeapPage getBeforeImage() {
        try {
            return new HeapPage(pid, oldData);
        } catch (IOException e) {
            e.printStackTrace();
            //should never happen -- we parsed it OK before!
            System.exit(1);
        }
        return null;
    }

    public void setBeforeImage() {
        oldData = getPageData().clone();
    }

    /**
     * @return the PageId associated with this page.
     */
    public HeapPageId getId() {
        // some code goes here
        return pid;
    }

    /**
     * Suck up tuples from the source file.
     */
    private Tuple readNextTuple(DataInputStream dis, int slotId) throws NoSuchElementException {
        // if associated bit is not set, read forward to the next tuple, and
        // return null.
        if (!isSlotUsed(slotId)) {
            for (int i = 0; i < td.getSize(); i++) {
                try {
                    dis.readByte();
                } catch (IOException e) {
                    throw new NoSuchElementException("error reading empty tuple");
                }
            }
            return null;
        }

        // read fields in the tuple
        Tuple t = new Tuple(td);
        RecordId rid = new RecordId(pid, slotId);
        t.setRecordId(rid);
        try {
            for (int j = 0; j < td.numFields(); j++) {
                Field f = td.getFieldType(j).parse(dis);
                t.setField(j, f);
            }
        } catch (java.text.ParseException e) {
            e.printStackTrace();
            throw new NoSuchElementException("parsing error!");
        }

        return t;
    }

    /**
     * Generates a byte array representing the contents of this page.
     * Used to serialize this page to disk.
     * <p>
     * The invariant here is that it should be possible to pass the byte
     * array generated by getPageData to the HeapPage constructor and
     * have it produce an identical HeapPage object.
     *
     * @return A byte array correspond to the bytes of this page.
     * @see #HeapPage
     */
    public byte[] getPageData() {
        int len = BufferPool.PAGE_SIZE;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
        DataOutputStream dos = new DataOutputStream(baos);

        // create the header of the page
        for (int i = 0; i < header.length; i++) {
            try {
                dos.writeByte(header[i]);
            } catch (IOException e) {
                // this really shouldn't happen
                e.printStackTrace();
            }
        }

        // create the tuples
        for (int i = 0; i < tuples.length; i++) {

            // empty slot
            if (!isSlotUsed(i)) {
                for (int j = 0; j < td.getSize(); j++) {
                    try {
                        dos.writeByte(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                continue;
            }

            // non-empty slot
            for (int j = 0; j < td.numFields(); j++) {
                Field f = tuples[i].getField(j);
                try {
                    f.serialize(dos);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // padding
        // ����ֽ�0
        int zerolen = BufferPool.PAGE_SIZE - (header.length + td.getSize() * tuples.length); //- numSlots * td.getSize();
        byte[] zeroes = new byte[zerolen];
        try {
            dos.write(zeroes, 0, zerolen);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return baos.toByteArray();
    }

    /**
     * Static method to generate a byte array corresponding to an empty
     * HeapPage.
     * Used to add new, empty pages to the file. Passing the results of
     * this method to the HeapPage constructor will create a HeapPage with
     * no valid tuples in it.
     *
     * @return The returned ByteArray.
     */
    public static byte[] createEmptyPageData() {
        int len = BufferPool.PAGE_SIZE;
        return new byte[len]; //all 0
    }

    /**
     * Delete the specified tuple from the page;  the tuple should be updated to reflect
     * that it is no longer stored on any page.
     *
     * @param t The tuple to delete
     * @throws DbException if this tuple is not on this page, or tuple slot is
     *                     already empty.
     */
    public void deleteTuple(Tuple t) throws DbException {
        // some code goes here
        // not necessary for lab1
        RecordId tid = t.getRecordId();
        HeapPageId hpid = (HeapPageId) tid.getPageId();
        int tupleNum = tid.tupleno();
        if (!hpid.equals(pid) || !isSlotUsed(tupleNum)) {
            throw new DbException("this tuple is not on this page, or tuple slot is already empty");
        }
        tuples[tupleNum] = null;
        markSlotUsed(tupleNum, false);
    }

    /**
     * Adds the specified tuple to the page;  the tuple should be updated to reflect
     * that it is now stored on this page.
     *
     * @param t The tuple to add.
     * @throws DbException if the page is full (no empty slots) or tupledesc
     *                     is mismatch.
     */
    public void insertTuple(Tuple t) throws DbException {
        // some code goes here
        if (!td.equals(t.getTupleDesc())) throw new DbException("tupleDesc is mismatch");
        //��ʹ��getNumTuples() == 0���ж��Ƿ�û�п��õ�slot����ΪҪ�ҵ����õ�slot�����Ҫ����һ��tuples����
        //if(getNumTuples() == 0) throw new DbException("the page is full (no empty slots)");
        for (int i = 0; i < getNumTuples(); i++) {
            if (!isSlotUsed(i)) {
                tuples[i] = t;
                //�޸�tuple����Ϣ�����������ڴ洢�����page��
                t.setRecordId(new RecordId(pid, i));
                markSlotUsed(i, true);
                return;
            }
        }
        throw new DbException("the page is full (no empty slots)");
    }

    /**
     * Marks this page as dirty/not dirty and record that transaction
     * that did the dirtying
     */
    public void markDirty(boolean dirty, TransactionId tid) {
        // some code goes here
        // not necessary for lab1
        lastDirtyOperation = dirty ? tid : null;
    }

    /**
     * Returns the tid of the transaction that last dirtied this page, or null if the page is not dirty
     */
    public TransactionId isDirty() {
        // some code goes here
        // Not necessary for lab1
        return lastDirtyOperation;
    }

    /**
     * Returns the number of empty slots on this page.
     */
    // TODO: 17-6-8 �޸�Ϊ��һ���������洢�յ�slot������Ч�ʻ���ߣ���Ȼά��Ҳ����鷳��
    public int getNumEmptySlots() {
        // some code goes here
        int emptySlots = 0;
        for (int i = 0; i < getNumTuples(); i++) {
            if (!isSlotUsed(i)) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    public static void main(String[] args) {
        List<String> l = new ArrayList<>();
        l.add("a");
        l.add("b");
        for (String i : l) {
            if (i.equals("a")) {
                l.remove(i);
            }
        }
        Iterator<String> it = l.iterator();
        while (it.hasNext()) {
            String n = it.next();
            if (n.equals("b")) {
                l.remove(n);
            }
        }
        System.out.println(l);
    }

    /**
     * Returns true if associated slot on this page is filled.
     */
    public boolean isSlotUsed(int i) {
        // some code goes here
        //ע��ʹ�õ���big-ending
        //������18��slots������ȫ��used�ģ���ôheader�Ķ���������Ϊ[11111111, 11111111, 00000011]
        //���һ��byte��ǰ������0������Ӧslot

        int byteNum = i / 8;//�����ڵڼ����ֽ�
        int posInByte = i % 8;//�����ڸ��ֽڵĵڼ�λ,���������㣨������ΪJVM��big-ending��
        return isOne(header[byteNum], posInByte);
    }

    /**
     * @param target    Ҫ�жϵ�bit���ڵ�byte
     * @param posInByte Ҫ�жϵ�bit��byte�Ĵ��������ƫ��������0��ʼ
     * @return target��������ƫ����pos����bit�Ƿ�Ϊ1
     */
    private boolean isOne(byte target, int posInByte) {
        // �����byte��11111011,pos��2(Ҳ����0�Ǹ�bit��λ��)
        // ��ôֻ��������7-2=5λ����ͨ������λ���жϣ�ע��Ҫǿת
        return (byte) (target << (7 - posInByte)) < 0;
    }
//    ��һ��������
//    private static boolean isOne(byte target, int posInByte) {
//        // �����byte��11111011,pos��2(Ҳ����0�Ǹ�bit��λ��)
//        // ��ôֻ��������2λ����ͨ���Ƿ�����2���ж�
//        return (target >> posInByte) % 2 == 1;
//    }


    /**
     * ��ĳ��slot��ֵ��Ϊ1��0
     * Abstraction to fill or clear a slot on this page.
     */
    private void markSlotUsed(int i, boolean value) {
        // some code goes here
        // not necessary for lab1
        int byteNum = i / 8;//�����ڵڼ����ֽ�
        int posInByte = i % 8;//�����ڸ��ֽڵĵڼ�λ,���������㣨������ΪJVM��big-ending��
        header[byteNum] = editBitInByte(header[byteNum], posInByte, value);
    }

    /**
     * �޸�һ��byte��ָ��λ�õ�bit
     *
     * @param target    ���޸ĵ�byte
     * @param posInByte bit��λ����target��ƫ���������������Ҵ�0��ʼ�㣬ȡֵ��ΧΪ0��7
     * @param value     Ϊtrue�޸ĸ�bitΪ1,Ϊfalseʱ�޸�Ϊ0
     * @return �޸ĺ��byte
     */
    private byte editBitInByte(byte target, int posInByte, boolean value) {
        if (posInByte < 0 || posInByte > 7) {
            throw new IllegalArgumentException();
        }
        byte b = (byte) (1 << posInByte);//��1���bit�Ƶ�ָ��λ�ã�����posΪ3,valueΪtrue�����õ�00001000
        //���valueΪ1,ʹ���ֽ�00001000�Լ�"|"�������Խ�ָ��λ�ø�Ϊ1������λ�ò���
        //���valueΪ0,ʹ���ֽ�11110111�Լ�"&"�������Խ�ָ��λ�ø�Ϊ0������λ�ò���
        return value ? (byte) (target | b) : (byte) (target & ~b);
    }

    /**
     * @return an iterator over all tuples on this page (calling remove on this iterator throws an UnsupportedOperationException)
     * (note that this iterator shouldn't return tuples in empty slots!)
     */
    public Iterator<Tuple> iterator() {
        // some code goes here
        return new UsedTupleIterator();
    }

    private class UsedTupleIterator implements Iterator<Tuple> {

        /**
         * ���ӣ�header������������̸������ı仯����
         * headers: [00101000]
         * index:   [01234567]
         * pos:     [00011222]
         * pos���ҵ�һ��Ϊ1��bit֮��ż�һ
         */

        private int pos = 0;
        private int index = 0;//tuple������±�仯
        private int usedTuplesNum = getNumTuples() - getNumEmptySlots();

        @Override
        public boolean hasNext() {
            return index < getNumTuples() && pos < usedTuplesNum;
        }

        @Override
        public Tuple next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            for (; !isSlotUsed(index); index++) {
            }//��ѭ����ֱ���ҵ���ʹ�õ�(��Ӧ��slot�ǿյ�)tuple���ٷ���
            pos++;
            return tuples[index++];
        }
    }

}
