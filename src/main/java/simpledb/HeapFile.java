package simpledb;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @author Sam Madden
 * @see HeapPage#HeapPage
 */
public class HeapFile implements DbFile {

    private TupleDesc tupleDesc;

    private File file;

    private int numPage;

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f the file that stores the on-disk backing store for this heap
     *          file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        file = f;
        numPage = (int) (file.length() / BufferPool.PAGE_SIZE);
        tupleDesc = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return file.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return tupleDesc;
    }

    // see DbFile.java for javadocs

    /**
     * ����PageId�Ӵ��̶�ȡһ��ҳ��ע��˷���ֻӦ����BufferPool�౻ֱ�ӵ���
     * ��������Ҫpage�ĵط���Ҫͨ��BufferPool���ʡ���������ʵ�ֻ��湦��
     *
     * @param pid
     * @return ��ȡ�õ���Page
     * @throws IllegalArgumentException
     */
    public Page readPage(PageId pid) throws IllegalArgumentException {
        // some code goes here
        if (pid.getTableId() != getId()) {
            throw new IllegalArgumentException();
        }
        Page page = null;
        byte[] data = new byte[BufferPool.PAGE_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(getFile(), "r")) {
            // page��HeapFile��ƫ����
            int pos = pid.pageNumber() * BufferPool.PAGE_SIZE;
            raf.seek(pos);
            raf.read(data, 0, data.length);
            page = new HeapPage((HeapPageId) pid, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return page;
    }


    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for proj1
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(page.getId().pageNumber() * BufferPool.PAGE_SIZE);
            byte[] data = page.getPageData();
            raf.write(data);
        }
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return numPage;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        ArrayList<Page> affectedPages = new ArrayList<>();
        for (int i = 0; i < numPages(); i++) {
//            HeapPageId pid = new HeapPageId(getId(), i);
            HeapPageId pid = new HeapPageId(getId(), i);
            HeapPage page = null;
            try {
                page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (page.getNumEmptySlots() != 0) {
                //page��insertTuple�Ѿ������޸�tuple��Ϣ��������洢�ڸ�page��
                page.insertTuple(t);
                page.markDirty(true, tid);
                affectedPages.add(page);
                break;
            }
        }
        if (affectedPages.size() == 0) {//˵��page���Ѿ�����
            //����һ���µĿհ׵�Page
//            HeapPageId npid = new HeapPageId(getId(), numPages());
            HeapPageId npid = new HeapPageId(getId(), numPages());
            HeapPage blankPage = new HeapPage(npid, HeapPage.createEmptyPageData());
            numPage++;
            //����д�����
            writePage(blankPage);
            //ͨ��BufferPool�����ʸ��µ�page
            HeapPage newPage = null;
            try {
                newPage = (HeapPage) Database.getBufferPool().getPage(tid, npid, Permissions.READ_WRITE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            newPage.insertTuple(t);
            newPage.markDirty(true, tid);
            affectedPages.add(newPage);
        }
        return affectedPages;
        // not necessary for proj1
    }

    // see DbFile.java for javadocs
    public Page deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        PageId pid = t.getRecordId().getPageId();
        HeapPage affectedPage = null;
        for (int i = 0; i < numPages(); i++) {
            if (i == pid.pageNumber()) {
                try {
                    affectedPage = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                affectedPage.deleteTuple(t);
                affectedPage.markDirty(true, tid);
            }
        }
        if (affectedPage == null) {
            throw new DbException("tuple " + t + " is not in this table");
        }
        return affectedPage;
        // not necessary for proj1
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFileIterator(tid);
    }

    /**
     * �������ʵ��ʱ�в����ɻ󣬲ο��˱��˵Ĵ�����������һЩ�㣺
     * 1.tableid����heapfile��id����ͨ��getId��������������Ǵ�0��ʼ�ģ����տγ�Դ���Ƽ��������ļ��Ĺ�ϣ�롣��
     * 2.PageId�Ǵ�0��ʼ�ġ�����(����˵�ˣ��������Ĭ�ϵ�ô��˭֪�������ҵ������ǲ��Ǵ�0��ʼ������)
     * 3.transactionId�����������ҷǳ���������֪������������iterator�����ĵ����߻��ṩ��Ӧ�����Ժ��½ڵ�����
     * 4.�Ҿ��ñ��˵�һ���뷨ͦ�ã����Ǵ洢һ����ǰ���ڱ�����ҳ��tuples�����������ã�����һҳһҳ������
     */
    private class HeapFileIterator implements DbFileIterator {

        private int pagePos;

        private Iterator<Tuple> tuplesInPage;

        private TransactionId tid;

        public HeapFileIterator(TransactionId tid) {
            this.tid = tid;
        }

        public Iterator<Tuple> getTuplesInPage(HeapPageId pid) throws TransactionAbortedException, DbException {
            // ����ֱ��ʹ��HeapFile��readPage����������ͨ��BufferPool�����page�����ɼ�readPage()������Javadoc
            HeapPage page = null;
            try {
                page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return page.iterator();
        }

        @Override
        public void open() throws DbException, TransactionAbortedException {
            pagePos = 0;
            HeapPageId pid = new HeapPageId(getId(), pagePos);
            //���ص�һҳ��tuples
            tuplesInPage = getTuplesInPage(pid);
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if (tuplesInPage == null) {
                //˵���Ѿ����ر�
                return false;
            }
            //�����ǰҳ����tupleδ����
            if (tuplesInPage.hasNext()) {
                return true;
            }
            //��������굱ǰҳ�������Ƿ���ҳδ����
            //ע��Ҫ��һ��������forѭ����һ���ж��߼�����������<���ȣ���ͬ������Ϊ����Ҫ�ڽ����������н�pagePos��1��ʹ��
            //�������⣬�����Լ���һ�������������й���
            if (pagePos < numPages() - 1) {
                pagePos++;
                HeapPageId pid = new HeapPageId(getId(), pagePos);
                tuplesInPage = getTuplesInPage(pid);
                //��ʱ����ֱ��return ture���п��ܷ��ص��µĵ������ǲ�����tuple��
                return tuplesInPage.hasNext();
            } else return false;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if (!hasNext()) {
                throw new NoSuchElementException("not opened or no tuple remained");
            }
            return tuplesInPage.next();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            //ֱ�ӳ�ʼ��һ�Ρ���������
            open();
        }

        @Override
        public void close() {
            pagePos = 0;
            tuplesInPage = null;
        }
    }

}