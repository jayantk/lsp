
public class Tree<T> {
    private Node<T> root;

    public Tree(T rootData) {
        root = new Node<T>();
        root.data = rootData;
        root.left = null;
        root.right = null;
    }

    public static class Node<T> {
        private T data;
        private Node<T> parent;
        private Node<T> left;
        private Node<T> right;
    }
}
