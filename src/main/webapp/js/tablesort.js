// https://stackoverflow.com/questions/14267781/sorting-html-table-with-javascript
var __getTableCellValue = function(tr, idx)
{
    return tr.children[idx].innerText || tr.children[idx].textContent;
}

var __tableCellComparer = function(idx, asc)
{
    return function(a, b)
    {
        return function(v1, v2)
        {
            return v1 !== '' && v2 !== '' && !isNaN(v1) && !isNaN(v2)
                   ? v1 - v2
                   : v1.toString().localeCompare(v2);
        }
        (__getTableCellValue(asc ? a : b, idx), __getTableCellValue(asc ? b : a, idx));
    }
};

/** Make table sortable via click on headers
 * 
 *  <p>Will always skip sorting the first (header) row.
 * 
 *  @param table jQuery object for table
 *  @param skip_last Skip sorting the last row (used for "summary")
 */
function makeTableSortable(table, skip_last)
{
    table.find("th").css("cursor", "pointer");
    table.find("th").click(event =>
    {
        let th = event.target;
        let table = th.closest('table');
        let rows = Array.from(table.querySelectorAll('tr:nth-child(n+2)'));
        let last;
        if (skip_last)
            last = rows.pop();
        console.log(rows);
        rows.sort(__tableCellComparer(Array.from(th.parentNode.children).indexOf(th), this.asc = !this.asc));
        
        if (skip_last)
            rows.push(last);
        rows.forEach(tr => table.appendChild(tr) );
    });
}
