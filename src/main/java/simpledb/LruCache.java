package simpledb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LruCache<K,V> {

    //��ŵ�ǰ�������Ŀ
    protected HashMap<K,Node> cachedEntries;

    //������������Ŀ����
    protected int capacity;

    //�������ݵ�ͷ���
    protected Node head;

    //���һ�����
    protected Node tail;

    protected class Node{
        Node front;
        Node next;
        K key;
        V value;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    public LruCache(int capacity) {
        this.capacity = capacity;
        cachedEntries = new HashMap<>(capacity);
        head = new Node(null, null);
    }

    /**
     * ɾ�����
     * @param ruNode the recently used Node
     */
    protected void unlink(Node ruNode) {
        //��������һ�����
        if (ruNode.next == null) {
            ruNode.front.next = null;
        } else {
            ruNode.front.next=ruNode.next;
            ruNode.next.front=ruNode.front;
        }
    }

    /**
     * �ѽڵ���뵽��ͷ��Ϊ��һ�����(��ͷ���֮��)
     * @param ruNode  the recently used Node
     */
    protected void linkFirst(Node ruNode) {
        Node first= this.head.next;
        this.head.next=ruNode;
        ruNode.front= this.head;
        ruNode.next=first;
        if (first == null) {
            tail = ruNode;
        } else {
            first.front=ruNode;
        }
    }

    /**
     * ɾ����������һ��Ԫ��
     */
    protected void removeTail() {
        Node newTail = tail.front;
        tail.front=null;
        newTail.next=null;
        tail=newTail;
    }

    /**
     *
     * @param key
     * @param value
     * @return     ��ɾ�����������Ŀ�����û�У�����null
     * @throws CacheException ���put��������
     */
    public synchronized V put(K key, V value) throws CacheException{
        if (key == null | value == null) {//���������nullֵ
            throw new IllegalArgumentException();
        }
        if (isCached(key)) {
            //�ý�������cache�У��������ֵ��Ȼ��������ʹ�õ���Ŀ������null(��Ϊû�б�ɾ������Ŀ)
            Node ruNode = cachedEntries.get(key);
            ruNode.value=value;
            unlink(ruNode);
            linkFirst(ruNode);
            return null;
        } else  {
            //�����ڵĻ����ж��Ƿ��Ѿ��ﵽ�������ǵĻ�Ҫ��ɾ��β�������䷵��
            //��û�еĻ�ֻ��Ҫ�½���㣬Ȼ����뵽��ͷ������null
            V removed=null;
            if (cachedEntries.size() == capacity) {
                removed = cachedEntries.remove(tail.key).value;
                removeTail();
            }
            Node ruNode = new Node(key, value);
            linkFirst(ruNode);
            cachedEntries.put(key, ruNode);
            return removed;
        }
    }

    /**
     *
     * @param key
     * @return  ���ش����ڻ����е���Ŀ���������򷵻�null
     */
    public synchronized V get(K key) {
        if (isCached(key)) {
            //�������ʹ�õ���Ŀ
            Node ruNode = cachedEntries.get(key);
            if (tail == ruNode && ruNode.front != head) {
                //�����β�ڵ�����ǰһ����Ϊͷ��㣬������ǰһ���ڵ�Ϊ�µ�β�ڵ�
                tail = ruNode.front;
            }
            unlink(ruNode);
            linkFirst(ruNode);
            return ruNode.value;
        }
        return null;
    }

    public synchronized boolean isCached(K key) {
        return cachedEntries.containsKey(key);
    }

    protected void displayCache() {
        //���ڲ��Ե�
        Node n=head;
        while ((n = n.next) != null) {
            System.out.print(n.value+", ");
        }
        System.out.println();
    }

    /**
     *
     * @return ��ǰ���������value
     */
    public Iterator<V> iterator() {
        return new LruIter();
    }

    protected class LruIter implements Iterator<V> {
        Node n = head;

        @Override
        public synchronized boolean hasNext() {
            return n.next!=null;
        }

        @Override
        public synchronized V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            n=n.next;
            return n.value;
        }
    }
}