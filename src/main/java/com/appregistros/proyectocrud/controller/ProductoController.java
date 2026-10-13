package com.appregistros.proyectocrud.controller;

import connection.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import com.appregistros.proyectocrud.model.Producto;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProductoController {

    // ====================== Formulario ======================
    @FXML
    private TextField txtCodigo;
    @FXML
    private TextField txtNombre;
    @FXML
    private TextField txtPrecio;
    @FXML
    private TextField txtStock;
    @FXML
    private ComboBox<String> cmbCategoria;
    @FXML
    private ComboBox<String> cmbMarca;
    @FXML
    private DatePicker dpFecha;
    @FXML
    private TextField txtDescripcion;

    @FXML
    private Button btnNuevo;
    @FXML
    private Button btnGuardar;
    @FXML
    private Button btnActualizar;
    @FXML
    private Button btnEliminar;

    // ====================== Filtros ======================
    @FXML
    private TextField txtBuscar;
    @FXML
    private ComboBox<String> cmbFiltroCategoria;
    @FXML
    private DatePicker dpDesde;
    @FXML
    private DatePicker dpHasta;
    @FXML
    private TextField txtPrecioMin;
    @FXML
    private TextField txtPrecioMax;
    @FXML
    private CheckBox chkSoloConStock;
    @FXML
    private ComboBox<String> cmbOrdenar;
    @FXML
    private Button btnBuscar;
    @FXML
    private Button btnLimpiar;

    // ====================== Tabla ======================
    @FXML
    private TableView<Producto> tblProductos;
    @FXML
    private TableColumn<Producto, String> colCodigo;
    @FXML
    private TableColumn<Producto, String> colNombre;
    @FXML
    private TableColumn<Producto, String> colCategoria;
    @FXML
    private TableColumn<Producto, String> colMarca;
    @FXML
    private TableColumn<Producto, Double> colPrecio;
    @FXML
    private TableColumn<Producto, Integer> colStock;
    @FXML
    private TableColumn<Producto, LocalDate> colFecha;

    private final ObservableList<Producto> productos = FXCollections.observableArrayList();

    // ====================== Inicialización ======================
    @FXML
    public void initialize() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colCategoria.setCellValueFactory(new PropertyValueFactory<>("categoria"));
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));
        colPrecio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaCreacion"));

        tblProductos.setItems(productos);

        // Estas opciones funcionan como whitelist del ORDER BY
        cmbOrdenar.setItems(FXCollections.observableArrayList(
                "Nombre",
                "Precio menor a mayor",
                "Precio mayor a menor",
                "Fecha más reciente",
                "Fecha más antigua",
                "Stock mayor a menor",
                "Stock menor a mayor"));
        cmbOrdenar.setValue("Nombre");

        cargarCombos();

        // false = no mostrar alerta de "sin resultados" al abrir la ventana
        cargarProductos(false);

        tblProductos.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, seleccionado) -> {
                    if (seleccionado != null) mostrarEnFormulario(seleccionado);
                });
    }

    private void cargarCombos() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            cargarCombo(connection, "SELECT nombre FROM catalogos.categorias WHERE estado = 1 ORDER BY nombre", cmbCategoria);
            cargarCombo(connection, "SELECT nombre FROM catalogos.marcas WHERE estado = 1 ORDER BY nombre", cmbMarca);
            cargarCombo(connection, "SELECT nombre FROM catalogos.categorias WHERE estado = 1 ORDER BY nombre", cmbFiltroCategoria);
        } catch (SQLException ex) {
            System.out.println("error al cargar combos: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void cargarCombo(Connection connection, String sql, ComboBox<String> comboBox) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            comboBox.getItems().clear();
            while (resultSet.next()) {
                comboBox.getItems().add(resultSet.getString(1));
            }
        } catch (SQLException ex) {
            System.out.println("error al consultar combo: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void mostrarEnFormulario(Producto p) {
        txtCodigo.setText(p.getCodigo());
        txtNombre.setText(p.getNombre());
        txtPrecio.setText(String.valueOf(p.getPrecio()));
        txtStock.setText(String.valueOf(p.getStock()));
        txtDescripcion.setText(p.getDescripcion());
        cmbCategoria.setValue(p.getCategoria());
        cmbMarca.setValue(p.getMarca());
        dpFecha.setValue(p.getFechaCreacion());
    }

    // ====================== Acciones del formulario ======================
    @FXML
    private void onNuevo() {
        limpiarFormulario();
        tblProductos.getSelectionModel().clearSelection();
    }

    // INSERT
    @FXML
    private void onGuardar() {
        if (!validarFormulario()) return;

        String codigo = txtCodigo.getText().trim();

        if (existeCodigo(codigo, 0)) {
            mostrarAlerta(Alert.AlertType.WARNING, "Ya existe un producto activo con el código " + codigo + ".");
            return;
        }

        String sql = """
                INSERT INTO catalogos.productos
                    (codigo, nombre, descripcion, precio, stock, fecha_creacion, id_categoria, id_marca, estado)
                VALUES (?, ?, ?, ?, ?, ?,
                    (SELECT id_categoria FROM catalogos.categorias WHERE nombre = ? AND estado = 1 LIMIT 1),
                    (SELECT id_marca FROM catalogos.marcas WHERE nombre = ? AND estado = 1 LIMIT 1),
                    1)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            asignarParametrosFormulario(ps);
            ps.executeUpdate();

            mostrarAlerta(Alert.AlertType.INFORMATION, "Producto guardado correctamente.");
            limpiarFormulario();
            // Se limpian los filtros para que el producto nuevo se vea en la tabla
            onLimpiarFiltros();

        } catch (SQLException ex) {
            manejarErrorBD("No se pudo guardar el producto.", ex);
        }
    }

    // UPDATE
    @FXML
    private void onActualizar() {
        Producto seleccionado = tblProductos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Selecciona un producto de la tabla.");
            return;
        }
        if (!validarFormulario()) return;

        String codigo = txtCodigo.getText().trim();

        if (existeCodigo(codigo, seleccionado.getIdProducto())) {
            mostrarAlerta(Alert.AlertType.WARNING, "Ya existe otro producto activo con el código " + codigo + ".");
            return;
        }

        String sql = """
                UPDATE catalogos.productos
                SET codigo = ?, nombre = ?, descripcion = ?, precio = ?, stock = ?, fecha_creacion = ?,
                    id_categoria = (SELECT id_categoria FROM catalogos.categorias WHERE nombre = ? AND estado = 1 LIMIT 1),
                    id_marca = (SELECT id_marca FROM catalogos.marcas WHERE nombre = ? AND estado = 1 LIMIT 1)
                WHERE id_producto = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            asignarParametrosFormulario(ps);
            ps.setInt(9, seleccionado.getIdProducto());
            ps.executeUpdate();

            mostrarAlerta(Alert.AlertType.INFORMATION, "Producto actualizado correctamente.");
            limpiarFormulario();
            cargarProductos(false);

        } catch (SQLException ex) {
            manejarErrorBD("No se pudo actualizar el producto.", ex);
        }
    }

    // DELETE lógico (estado = 0)
    @FXML
    private void onEliminar() {
        Producto seleccionado = tblProductos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Selecciona un producto de la tabla.");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("¿Eliminar el producto \"" + seleccionado.getNombre() + "\"?");
        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String sql = "UPDATE catalogos.productos SET estado = 0 WHERE id_producto = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, seleccionado.getIdProducto());
            ps.executeUpdate();

            mostrarAlerta(Alert.AlertType.INFORMATION, "Producto eliminado correctamente.");
            limpiarFormulario();
            cargarProductos(false);

        } catch (SQLException ex) {
            manejarErrorBD("No se pudo eliminar el producto.", ex);
        }
    }

    // Asigna los 8 primeros parámetros comunes de INSERT y UPDATE
    private void asignarParametrosFormulario(PreparedStatement ps) throws SQLException {
        LocalDate fecha = dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();

        ps.setString(1, txtCodigo.getText().trim());
        ps.setString(2, txtNombre.getText().trim());
        ps.setString(3, txtDescripcion.getText() == null ? "" : txtDescripcion.getText().trim());
        ps.setBigDecimal(4, new BigDecimal(txtPrecio.getText().trim()));
        ps.setInt(5, Integer.parseInt(txtStock.getText().trim()));
        ps.setDate(6, Date.valueOf(fecha));
        ps.setString(7, cmbCategoria.getValue());
        ps.setString(8, cmbMarca.getValue());
    }

    // Verifica si ya hay otro producto activo con ese código (idExcluir = 0 en INSERT)
    private boolean existeCodigo(String codigo, int idExcluir) {
        String sql = "SELECT 1 FROM catalogos.productos WHERE codigo = ? AND estado = 1 AND id_producto <> ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ps.setInt(2, idExcluir);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            manejarErrorBD("No se pudo verificar el código.", ex);
            return true; // ante error, no continuar con el guardado
        }
    }

    private void manejarErrorBD(String mensaje, SQLException ex) {
        System.out.println(mensaje + " " + ex.getMessage());
        ex.printStackTrace();
        mostrarAlerta(Alert.AlertType.ERROR, mensaje + "\n" + ex.getMessage());
    }

    // Botón "Actualizar tabla": vuelve a consultar la BD respetando los filtros actuales
    @FXML
    private void ActualizarTabla() {
        cargarProductos(false);
    }

    // ====================== Acciones de filtros ======================
    @FXML
    private void onBuscar() {
        // true = si no hay resultados, avisar al usuario
        cargarProductos(true);
    }

    @FXML
    private void onLimpiarFiltros() {
        txtBuscar.clear();
        cmbFiltroCategoria.setValue(null);
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        txtPrecioMin.clear();
        txtPrecioMax.clear();
        chkSoloConStock.setSelected(false);
        cmbOrdenar.setValue("Nombre");
        cargarProductos(false);
    }

    // ====================== Utilidades ======================
    private boolean validarFormulario() {
        if (txtCodigo.getText().isBlank() || txtNombre.getText().isBlank()
                || cmbCategoria.getValue() == null || cmbMarca.getValue() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Código, nombre, categoría y marca son obligatorios.");
            return false;
        }
        try {
            double precio = Double.parseDouble(txtPrecio.getText().trim());
            int stock = Integer.parseInt(txtStock.getText().trim());
            if (precio <= 0) {
                mostrarAlerta(Alert.AlertType.WARNING, "El precio debe ser mayor que 0.");
                return false;
            }
            if (stock < 0) {
                mostrarAlerta(Alert.AlertType.WARNING, "El stock no puede ser negativo.");
                return false;
            }
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Precio y stock deben ser numéricos.");
            return false;
        }
        return true;
    }

    private void limpiarFormulario() {
        txtCodigo.clear();
        txtNombre.clear();
        txtPrecio.clear();
        txtStock.clear();
        txtDescripcion.clear();
        cmbCategoria.setValue(null);
        cmbMarca.setValue(null);
        dpFecha.setValue(null);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }

    // ====================== BÚSQUEDA PARAMETRIZADA ======================
    /**
     * Consulta los productos aplicando todos los filtros de la sección
     * "Búsqueda y filtros".
     *
     * @param avisarSinResultados si es true y la consulta no devuelve filas,
     *                            se muestra una alerta informativa.
     *                            (En initialize() debe ser false: JavaFX no
     *                            permite showAndWait durante la carga de la vista.)
     */
    private void cargarProductos(boolean avisarSinResultados) {

        // ---------- 1. Leer y validar los filtros ----------
        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        String categoria = cmbFiltroCategoria.getValue();
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        boolean soloConStock = chkSoloConStock.isSelected();

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "La fecha 'Desde' no puede ser posterior a la fecha 'Hasta'.");
            return;
        }

        BigDecimal precioMin = null;
        BigDecimal precioMax = null;
        try {
            if (txtPrecioMin.getText() != null && !txtPrecioMin.getText().isBlank()) {
                precioMin = new BigDecimal(txtPrecioMin.getText().trim());
            }
            if (txtPrecioMax.getText() != null && !txtPrecioMax.getText().isBlank()) {
                precioMax = new BigDecimal(txtPrecioMax.getText().trim());
            }
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "El precio mínimo y el precio máximo deben ser numéricos.");
            return;
        }

        if (precioMin != null && precioMax != null && precioMin.compareTo(precioMax) > 0) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "El precio mínimo no puede ser mayor que el precio máximo.");
            return;
        }

        // ---------- 2. Construir el SQL dinámico ----------
        // La consulta siempre incluye p.estado = 1 para no mostrar
        // productos eliminados lógicamente.
        StringBuilder sql = new StringBuilder("""
                SELECT p.id_producto, p.codigo, p.nombre, p.descripcion,
                       p.precio, p.stock, p.fecha_creacion,
                       c.nombre AS categoria, m.nombre AS marca
                FROM catalogos.productos p
                INNER JOIN catalogos.categorias c ON p.id_categoria = c.id_categoria
                INNER JOIN catalogos.marcas m ON p.id_marca = m.id_marca
                WHERE p.estado = 1
                """);

        // Valores que se enviarán al PreparedStatement, en el mismo orden de los ?
        List<Object> parametros = new ArrayList<>();

        // Código o nombre (búsqueda parcial, sin distinguir mayúsculas)
        if (!texto.isBlank()) {
            sql.append(" AND (p.codigo ILIKE ? OR p.nombre ILIKE ?) ");
            parametros.add("%" + texto + "%");
            parametros.add("%" + texto + "%");
        }

        // Categoría
        if (categoria != null) {
            sql.append(" AND c.nombre = ? ");
            parametros.add(categoria);
        }

        // Rango de fechas
        if (desde != null) {
            sql.append(" AND p.fecha_creacion >= ? ");
            parametros.add(desde);
        }
        if (hasta != null) {
            sql.append(" AND p.fecha_creacion <= ? ");
            parametros.add(hasta);
        }

        // Rango de precios
        if (precioMin != null) {
            sql.append(" AND p.precio >= ? ");
            parametros.add(precioMin);
        }
        if (precioMax != null) {
            sql.append(" AND p.precio <= ? ");
            parametros.add(precioMax);
        }

        // Solo productos con existencias (condición fija, sin parámetro)
        if (soloConStock) {
            sql.append(" AND p.stock > 0 ");
        }

        // ORDER BY: los nombres de columna no se pueden enviar como ?,
        // por eso se usa una whitelist con las opciones del ComboBox.
        String orden = cmbOrdenar.getValue() == null ? "Nombre" : cmbOrdenar.getValue();

        switch (orden) {
            case "Precio menor a mayor" -> sql.append(" ORDER BY p.precio ASC ");
            case "Precio mayor a menor" -> sql.append(" ORDER BY p.precio DESC ");
            case "Fecha más reciente"   -> sql.append(" ORDER BY p.fecha_creacion DESC ");
            case "Fecha más antigua"    -> sql.append(" ORDER BY p.fecha_creacion ASC ");
            case "Stock mayor a menor"  -> sql.append(" ORDER BY p.stock DESC ");
            case "Stock menor a mayor"  -> sql.append(" ORDER BY p.stock ASC ");
            default                     -> sql.append(" ORDER BY p.nombre ASC ");
        }

        // ---------- 3. Ejecutar la consulta ----------
        productos.clear();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {

            // Asignar los parámetros dinámicamente
            for (int i = 0; i < parametros.size(); i++) {
                Object valor = parametros.get(i);

                if (valor instanceof LocalDate fecha) {
                    ps.setDate(i + 1, Date.valueOf(fecha));
                } else if (valor instanceof BigDecimal numero) {
                    ps.setBigDecimal(i + 1, numero);
                } else {
                    ps.setObject(i + 1, valor);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    productos.add(crearProductoDesdeResultSet(rs));
                }
            }

            tblProductos.setItems(productos);

            if (avisarSinResultados && productos.isEmpty()) {
                mostrarAlerta(Alert.AlertType.INFORMATION,
                        "No existen productos que coincidan con los filtros aplicados.");
            }

        } catch (SQLException ex) {
            System.out.println("error al buscar productos: " + ex.getMessage());
            ex.printStackTrace();
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo realizar la búsqueda.");
        }
    }

    // Convierte una fila del ResultSet en un objeto Producto
    private Producto crearProductoDesdeResultSet(ResultSet rs) throws SQLException {
        return new Producto(
                rs.getInt("id_producto"),
                rs.getString("codigo"),
                rs.getString("descripcion"),
                rs.getString("nombre"),
                rs.getDouble("precio"),
                rs.getInt("stock"),
                rs.getDate("fecha_creacion").toLocalDate(),
                0,
                rs.getString("categoria"),
                0,
                rs.getString("marca"),
                1);
    }
}