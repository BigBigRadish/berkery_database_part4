package simpledb;

public class PageLruCache extends LruCache<PageId, Page> {

    public PageLruCache(int capacity) {
        super(capacity);
    }

    @Override
    public synchronized Page put(PageId key, Page value) throws CacheException {
        if (key == null | value == null) {//���������nullֵ
            throw new IllegalArgumentException();
        }
        if (isCached(key)) {
            //�ý�������cache�У��������ֵ��Ȼ��������ʹ�õ���Ŀ������null(��Ϊû�б�ɾ������Ŀ)
            Node ruNode = cachedEntries.get(key);
            ruNode.value = value;
            unlink(ruNode);
            linkFirst(ruNode);
            return null;
        } else {
            //�����ڵĻ����ж��Ƿ��Ѿ��ﵽ����
            //��������������ж�β�ڵ��Ƿ���dirty��page���������ȡ��ǰһ��page
            //������ɾ��β�������䷵��
            //δ���������Ļ�ֻ��Ҫ�½���㣬Ȼ����뵽��ͷ������null
            Page removed = null;
            if (cachedEntries.size() == capacity) {
                Page toRemoved = null;
                Node n = tail;
                while ((toRemoved = n.value).isDirty() != null) {
                    n = n.front;
                    if (n == head)
                        throw new CacheException("Page Cache is full and all pages in cache are dirty, not supported to put now");
                }
                //��������ɾ����node,�Լ�������ɾ��page
                removePage(toRemoved.getId());
                removed = cachedEntries.remove(toRemoved.getId()).value;
            }
            Node ruNode = new Node(key, value);
            linkFirst(ruNode);
            cachedEntries.put(key, ruNode);
            return removed;
        }
    }


    /**
     * ɾ��cache��pageId��Ӧ��page
     *
     * @param pid
     */
    private synchronized void removePage(PageId pid) {
        if (!isCached(pid)) {
            throw new IllegalArgumentException();
        }
        Node toRemoved = head;
        //���ﲻ��Ҫ��������β���жϣ�toRemovedΪnull������Ϊ������϶����ڸ�page
        while (!(toRemoved = toRemoved.next).key.equals(pid)) ;
        if (toRemoved == tail) {
            removeTail();
        } else {
            toRemoved.next.front = toRemoved.front;
            toRemoved.front.next = toRemoved.next;
        }
    }

    /**
     * ��pid��Ӧ��page�Ӵ������ٴζ��룬������ָ�Ϊ�����и�page��״̬
     *
     * @param pid
     */
    public synchronized void reCachePage(PageId pid) {
        if (!isCached(pid)) {
            throw new IllegalArgumentException();
        }
        //���ʴ��̻�ø�page
        HeapFile table = (HeapFile) Database.getCatalog().getDbFile(pid.getTableId());
        HeapPage originalPage = (HeapPage) table.readPage(pid);
        Node node = new Node(pid, originalPage);
        cachedEntries.put(pid, node);
        Node toRemoved = head;
        //���ﲻ��Ҫ��������β���жϣ�toRemovedΪnull������Ϊ������϶����ڸ�page
        while (!(toRemoved = toRemoved.next).key.equals(pid)) ;
        node.front = toRemoved.front;
        node.next = toRemoved.next;
        toRemoved.front.next = node;
        if (toRemoved.next != null) {
            toRemoved.next.front = node;
        } else {
            //reCache����β�ڵ㣬��Ҫ�޸�tailָ��
            tail = node;
        }
    }
}
